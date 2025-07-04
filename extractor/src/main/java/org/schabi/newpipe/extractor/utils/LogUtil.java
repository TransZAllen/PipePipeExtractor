package org.schabi.newpipe.extractor.utils;

public class LogUtil {

    public static void logTree(String tag) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            StackTraceElement current = stack[3];
            String className = current.getClassName();
            String methodName = current.getMethodName();
            int lineNumber = current.getLineNumber();
            System.out.println(tag + " ==> " + className + "#" + methodName + " (line " + lineNumber + ")");
        } else {
            System.out.println(tag + " ==> <unknown>");
        }
    }

    public static void logTreeDeep(String tag, int depth) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        System.out.println(tag + " ==> Call stack:");

        for (int i = 3; i < 3 + depth && i < stack.length; i++) {
            StackTraceElement e = stack[i];
            System.out.println("  ↳ " + e.getClassName() + "#" + e.getMethodName() + " (line " + e.getLineNumber() + ")");
            //System.out.println("  ⬑ " + e.getClassName() + "#" + e.getMethodName() + " (line " + e.getLineNumber() + ")");
        }
    }

    public static void logWithMessage(String tag, String message) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            StackTraceElement current = stack[3];
            String className = current.getClassName();
            String methodName = current.getMethodName();
            int lineNumber = current.getLineNumber();
            System.out.println(tag + " ==> " + className + "#" + methodName + " (line " + lineNumber + ") : " + message);
        } else {
            System.out.println(tag + " ==> <unknown>: " + message);
        }
    }
}

