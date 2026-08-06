package com.xingledger.quickcapture;

public final class CaptureContract {
    public static final String ACTION_START = "com.xingledger.quickcapture.START";
    public static final String ACTION_START_AND_CAPTURE = "com.xingledger.quickcapture.START_AND_CAPTURE";
    public static final String ACTION_CAPTURE = "com.xingledger.quickcapture.CAPTURE";
    public static final String ACTION_STOP = "com.xingledger.quickcapture.STOP";
    public static final String ACTION_EXTERNAL_CAPTURE =
            "com.xingledger.quickcapture.action.CAPTURE_AND_BOOKKEEP";
    public static final String ACTION_RECENT_SCREENSHOT =
            "com.xingledger.quickcapture.action.RECENT_SCREENSHOT_AND_BOOKKEEP";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_DENSITY = "density";
    public static final String EXTRA_DRAFT = "draft";
    public static final String EXTRA_SCREENSHOT = "screenshot";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_SOURCE_APP = "source_app";
    public static final String EXTRA_SOURCE_PACKAGE = "source_package";
    public static final String EXTRA_AUTOMATION_SOURCE_APP = "extra_source_app";
    public static final String EXTRA_AUTOMATION_SOURCE_PACKAGE = "extra_source_package";
    public static final String EXTRA_RECORD_PATH = "record_path";

    public static final int RESULT_CAPTURED = 1;
    public static final int RESULT_ERROR = 2;

    private CaptureContract() {}
}
