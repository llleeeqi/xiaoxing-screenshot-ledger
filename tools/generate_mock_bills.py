#!/usr/bin/env python3
"""Create deterministic full-screen Chinese payment mockups for OCR testing."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1080, 1920
REGULAR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
OUTPUT = Path(__file__).resolve().parent.parent / "test-assets" / "mock-bills"


def font(size: int, bold: bool = False):
    return ImageFont.truetype(BOLD if bold else REGULAR, size)


def centered(draw, text, y, size, fill, bold=False):
    selected = font(size, bold)
    box = draw.textbbox((0, 0), text, font=selected)
    draw.text(((WIDTH - (box[2] - box[0])) / 2, y), text, font=selected, fill=fill)


def status_bar(draw, dark=True):
    color = "#202124" if dark else "#ffffff"
    draw.text((46, 28), "12:31", font=font(34, True), fill=color)
    draw.text((852, 28), "5G   ▮▮▮  86%", font=font(27), fill=color)


def row(draw, y, label, value, value_color="#202124"):
    draw.text((76, y), label, font=font(34), fill="#7a7a7a")
    value_font = font(34)
    box = draw.textbbox((0, 0), value, font=value_font)
    draw.text((WIDTH - 76 - (box[2] - box[0]), y), value, font=value_font, fill=value_color)
    draw.line((76, y + 72, WIDTH - 76, y + 72), fill="#eeeeee", width=2)


def alipay():
    image = Image.new("RGB", (WIDTH, HEIGHT), "#f5f6f8")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, WIDTH, 172), fill="#ffffff")
    status_bar(draw)
    draw.text((48, 103), "‹", font=font(62), fill="#202124")
    centered(draw, "账单详情", 112, 38, "#202124", True)
    draw.rounded_rectangle((38, 205, WIDTH - 38, 1725), radius=28, fill="#ffffff")
    draw.ellipse((456, 270, 624, 438), fill="#1677ff")
    centered(draw, "✓", 288, 92, "#ffffff", True)
    centered(draw, "支付成功", 476, 45, "#202124", True)
    centered(draw, "￥18.60", 555, 88, "#111111", True)
    centered(draw, "麦当劳（中山店）", 684, 40, "#333333")
    row(draw, 825, "订单金额", "23.60")
    row(draw, 925, "优惠", "5.00", "#ef5b25")
    row(draw, 1025, "付款方式", "花呗")
    row(draw, 1125, "商品说明", "餐饮消费")
    row(draw, 1225, "交易时间", "2026-08-06 12:31:09")
    row(draw, 1325, "交易号", "20260806123109001860")
    centered(draw, "支付宝", 1790, 30, "#1677ff", True)
    image.save(OUTPUT / "alipay_payment.png")


def wechat():
    image = Image.new("RGB", (WIDTH, HEIGHT), "#ededed")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, WIDTH, 170), fill="#ffffff")
    status_bar(draw)
    draw.text((48, 104), "‹", font=font(62), fill="#202124")
    centered(draw, "收款详情", 112, 38, "#202124", True)
    draw.rounded_rectangle((34, 205, WIDTH - 34, 1600), radius=24, fill="#ffffff")
    draw.ellipse((456, 270, 624, 438), fill="#07c160")
    centered(draw, "✓", 288, 92, "#ffffff", True)
    centered(draw, "收款成功", 478, 46, "#202124", True)
    centered(draw, "¥128.00", 566, 90, "#111111", True)
    row(draw, 760, "付款方", "张三")
    row(draw, 870, "收款方式", "微信零钱")
    row(draw, 980, "商品", "周末聚餐 AA")
    row(draw, 1090, "收款时间", "2026-08-06 18:42:16")
    row(draw, 1200, "交易单号", "42000027882026080612800")
    centered(draw, "微信支付", 1768, 32, "#07a653", True)
    image.save(OUTPUT / "wechat_receipt.png")


def bank():
    image = Image.new("RGB", (WIDTH, HEIGHT), "#f7f4fb")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, WIDTH, 610), fill="#5b3f95")
    status_bar(draw, dark=False)
    draw.text((48, 105), "‹", font=font(62), fill="#ffffff")
    centered(draw, "转账结果", 112, 38, "#ffffff", True)
    draw.ellipse((456, 238, 624, 406), fill="#ffffff")
    centered(draw, "✓", 255, 92, "#5b3f95", True)
    centered(draw, "转账成功", 445, 46, "#ffffff", True)
    centered(draw, "￥1,234.50", 520, 76, "#ffffff", True)
    draw.rounded_rectangle((36, 650, WIDTH - 36, 1680), radius=28, fill="#ffffff")
    row(draw, 730, "收款人", "李晓明")
    row(draw, 850, "转出账户", "招商银行储蓄卡(1234)")
    row(draw, 970, "转入账户", "浦发银行储蓄卡(5678)")
    row(draw, 1090, "转账说明", "房租")
    row(draw, 1210, "交易时间", "2026-08-06 09:18:32")
    row(draw, 1330, "交易流水", "62170020260806091832")
    centered(draw, "手机银行", 1780, 32, "#5b3f95", True)
    image.save(OUTPUT / "bank_transfer.png")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    alipay()
    wechat()
    bank()
    for path in sorted(OUTPUT.glob("*.png")):
        print(path)


if __name__ == "__main__":
    main()
