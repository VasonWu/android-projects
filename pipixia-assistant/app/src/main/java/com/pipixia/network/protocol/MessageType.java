package com.pipixia.network.protocol;

public class MessageType {
    public static final String TYPE_INPUT = "input";
    public static final String TYPE_CANCEL = "cancel";
    public static final String TYPE_OUTPUT = "output";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_EXIT = "exit";

    // Client message types
    public static final String TYPE_SESSION_CREATE = "session_create";
    public static final String TYPE_SESSION_SELECT = "session_select";
    public static final String TYPE_SESSION_LIST = "session_list";
    public static final String TYPE_PROCESS_START = "process_start";
    public static final String TYPE_PROCESS_STOP = "process_stop";
    public static final String TYPE_PROCESS_STATUS = "process_status";

    // Server message types
    public static final String TYPE_SESSION_CREATED = "session_created";
    public static final String TYPE_SESSION_SELECTED = "session_selected";
    public static final String TYPE_PROCESS_STARTED = "process_started";
    public static final String TYPE_PROCESS_CRASHED = "process_crashed";
    public static final String TYPE_PROCESS_STOPPED = "process_stopped";
}
