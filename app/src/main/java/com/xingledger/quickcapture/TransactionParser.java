package com.xingledger.quickcapture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristics for common WeChat, Alipay, UnionPay and banking result screens. */
public final class TransactionParser {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d.])([¥￥$]?\\s*[+-]?\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2}))(?!\\d)");
    private static final Pattern TIME = Pattern.compile(
            "(20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}(?:日)?(?:\\s+|\\s*[,，]?\\s*)(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?)");
    private static final Pattern EXPLICIT_SHOP = Pattern.compile(
            "(?:商户(?!单号)|商家|收款方(?!式)|付款方(?!式)|付款给|交易对象|对方账户|收款人)[：:]?\\s*(.*)");
    private static final Pattern EXPLICIT_ACCOUNT = Pattern.compile(
            "(?:付款方式|支付方式|收款方式|付款账户|收款账户|转出账户|扣款账户)[：:]?\\s*(.*)");
    private static final Pattern EXPLICIT_REMARK = Pattern.compile(
            "(?:商品说明|商品|备注|转账说明|付款说明)[：:]?\\s*(.*)");

    private TransactionParser() {}

    public static TransactionDraft parse(List<OcrLine> input) {
        List<OcrLine> lines = new ArrayList<>();
        for (OcrLine line : input) {
            if (line != null && !line.text.trim().isEmpty()) lines.add(line);
        }
        lines.sort(Comparator.comparingInt((OcrLine line) -> line.top).thenComparingInt(line -> line.left));

        TransactionDraft draft = new TransactionDraft();
        StringBuilder raw = new StringBuilder();
        for (OcrLine line : lines) {
            if (raw.length() > 0) raw.append('\n');
            raw.append(line.text);
        }
        draft.rawText = raw.toString();
        String all = draft.rawText.replace(" ", "");

        draft.type = detectType(all);
        draft.channel = detectChannel(all);
        draft.amount = detectAmount(lines);
        draft.time = detectTime(lines);
        draft.shop = detectShop(lines);
        draft.account = detectAccount(lines, draft.channel);
        draft.remark = detectRemark(lines);

        if ("转账".equals(draft.type)) {
            String second = findValue(lines, Pattern.compile("(?:转入账户|收款账户)[：:]?\\s*(.*)"));
            if (!second.equals(draft.account)) draft.account2 = second;
        }
        return draft;
    }

    private static String detectType(String all) {
        if (containsAny(all, "退款成功", "已退款", "退款到账", "退回成功")) return "收入";
        if (containsAny(all, "收款成功", "已收款", "收款到账", "收入到账", "已到账")) return "收入";
        if (containsAny(all, "转账成功", "转账详情", "转账记录")) return "转账";
        return "支出";
    }

    private static String detectChannel(String all) {
        if (containsAny(all, "微信支付", "微信零钱", "零钱通", "微信转账")) return "微信";
        if (containsAny(all, "支付宝", "余额宝", "花呗")) return "支付宝";
        if (containsAny(all, "云闪付", "银联")) return "云闪付";
        if (containsAny(all, "数字人民币", "数字钱包")) return "数字人民币";
        if (containsAny(all, "手机银行", "招商银行", "浦发银行", "工商银行", "建设银行", "农业银行", "中国银行")) return "手机银行";
        return "";
    }

    private static String detectAmount(List<OcrLine> lines) {
        Candidate best = null;
        for (int i = 0; i < lines.size(); i++) {
            String text = normalizePunctuation(lines.get(i).text);
            Matcher matcher = AMOUNT.matcher(text);
            while (matcher.find()) {
                String token = matcher.group(1);
                String normalized = normalizeAmount(token);
                if (normalized.isEmpty()) continue;
                String context = neighborContext(lines, i);
                int score = amountScore(text, context, token, normalized);
                Candidate candidate = new Candidate(normalized, score);
                if (best == null || candidate.score > best.score) best = candidate;
            }
        }
        return best == null ? "" : best.value;
    }

    private static int amountScore(String line, String context, String original, String normalized) {
        int score = 10;
        String compact = context.replace(" ", "");
        if (containsAny(compact, "实付", "付款金额", "收款金额", "支付金额", "转账金额", "退款金额", "交易金额", "订单金额", "合计", "总计")) score += 100;
        else if (containsAny(compact, "支付成功", "付款成功", "收款成功", "转账成功", "交易成功", "退款成功")) score += 55;
        if (original.contains("¥") || original.contains("￥")) score += 40;
        if (line.trim().matches("^[¥￥$+\\-\\s]*[\\d,.]+$")) score += 25;
        if (containsAny(line, "优惠", "折扣", "红包", "余额", "原价", "手续费")) score -= 80;
        if (containsAny(line, "订单号", "交易号", "商户单号", "尾号", "手机号")) score -= 150;
        if (TIME.matcher(line).find() || normalized.startsWith("20") && normalized.length() >= 4) score -= 120;
        try {
            BigDecimal value = new BigDecimal(normalized);
            if (value.signum() <= 0) score -= 100;
            if (value.compareTo(new BigDecimal("100000000")) > 0) score -= 100;
        } catch (NumberFormatException ignored) {
            score -= 200;
        }
        return score;
    }

    private static String normalizeAmount(String value) {
        String clean = value.replaceAll("[¥￥$,+\\s]", "");
        if (clean.startsWith("-")) clean = clean.substring(1);
        try {
            BigDecimal number = new BigDecimal(clean);
            return number.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException error) {
            return "";
        }
    }

    private static String detectTime(List<OcrLine> lines) {
        for (OcrLine line : lines) {
            Matcher matcher = TIME.matcher(line.text);
            if (matcher.find()) return matcher.group(1).replace('年', '-').replace('月', '-').replace("日", "").replace('/', '-').replace('.', '-');
        }
        return "";
    }

    private static String detectShop(List<OcrLine> lines) {
        String explicit = findValue(lines, EXPLICIT_SHOP);
        if (!explicit.isEmpty()) return cleanValue(explicit);

        int amountIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (AMOUNT.matcher(normalizePunctuation(lines.get(i).text)).find()) {
                amountIndex = i;
                break;
            }
        }
        if (amountIndex >= 0) {
            for (int distance = 1; distance <= 4; distance++) {
                for (int index : new int[]{amountIndex - distance, amountIndex + distance}) {
                    if (index < 0 || index >= lines.size()) continue;
                    String candidate = cleanValue(lines.get(index).text);
                    if (looksLikeShop(candidate)) return candidate;
                }
            }
        }
        return "";
    }

    private static boolean looksLikeShop(String value) {
        if (value.length() < 2 || value.length() > 40) return false;
        if (AMOUNT.matcher(normalizePunctuation(value)).find() || TIME.matcher(value).find()) return false;
        return !containsAny(value, "支付成功", "付款成功", "收款成功", "转账成功", "交易成功", "账单详情", "订单信息", "付款方", "收款方", "收款人", "付款方式", "支付方式", "收款方式", "交易时间", "创建时间", "订单号", "交易号", "查看", "完成", "返回", "账单", "服务", "优惠", "实付", "金额");
    }

    private static String detectAccount(List<OcrLine> lines, String channel) {
        String explicit = cleanValue(findValue(lines, EXPLICIT_ACCOUNT));
        if (!explicit.isEmpty()) return explicit;
        for (OcrLine line : lines) {
            String text = line.text;
            if (containsAny(text, "零钱通", "微信零钱", "余额宝", "支付宝余额", "花呗", "信用卡", "储蓄卡", "借记卡")) {
                return cleanValue(text.replaceFirst(".*?(?:付款方式|支付方式)[：:]?", ""));
            }
        }
        if ("微信".equals(channel)) return "微信零钱";
        if ("支付宝".equals(channel)) return "支付宝余额";
        return "";
    }

    private static String detectRemark(List<OcrLine> lines) {
        String value = cleanValue(findValue(lines, EXPLICIT_REMARK));
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private static String findValue(List<OcrLine> lines, Pattern pattern) {
        for (int i = 0; i < lines.size(); i++) {
            OcrLine line = lines.get(i);
            Matcher matcher = pattern.matcher(line.text);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.isEmpty() && !isFieldLabel(value)) return value;
                String closest = "";
                int closestScore = Integer.MAX_VALUE;
                for (int j = 0; j < lines.size(); j++) {
                    if (j == i) continue;
                    OcrLine neighbor = lines.get(j);
                    int verticalDistance = Math.abs(neighbor.top - line.top);
                    if (verticalDistance > 110) continue;
                    String candidate = cleanValue(neighbor.text);
                    if (candidate.isEmpty() || isFieldLabel(candidate)) continue;
                    int leftPenalty = neighbor.left >= line.left ? 0 : 10_000;
                    int score = leftPenalty + verticalDistance * 20 + Math.abs(neighbor.left - line.left);
                    if (score < closestScore) {
                        closest = candidate;
                        closestScore = score;
                    }
                }
                if (!closest.isEmpty()) return closest;
            }
        }
        return "";
    }

    private static boolean isFieldLabel(String value) {
        return value.matches("^(商户|商家|收款方|付款方|付款给|交易对象|对方账户|收款人|付款方式|支付方式|收款方式|付款账户|收款账户|转出账户|转入账户|扣款账户|商品说明|商品|备注|转账说明|付款说明|交易时间|收款时间|订单号|交易号|交易单号|交易流水)$");
    }

    private static String neighborContext(List<OcrLine> lines, int index) {
        StringBuilder result = new StringBuilder(lines.get(index).text);
        if (index > 0) result.append(' ').append(lines.get(index - 1).text);
        if (index + 1 < lines.size()) result.append(' ').append(lines.get(index + 1).text);
        return result.toString();
    }

    private static String normalizePunctuation(String value) {
        return value.replace('，', ',').replace('。', '.').replace('－', '-').replace('＋', '+');
    }

    private static String cleanValue(String value) {
        return value.replaceAll("^[：:\\s]+|[：:\\s]+$", "").trim();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static final class Candidate {
        final String value;
        final int score;
        Candidate(String value, int score) {
            this.value = value;
            this.score = score;
        }
    }
}
