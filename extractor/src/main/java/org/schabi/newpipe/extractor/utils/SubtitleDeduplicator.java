package org.schabi.newpipe.extractor.utils;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.*;
import java.util.*;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.utils.LogUtil;

public class SubtitleDeduplicator {
    private static File CACHE_DIR = new File(System.getProperty("java.io.tmpdir"), "pipepipe_subtitle_cache");

    static {
        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
    }

    public static void setCacheDirPath(String path) {
        CACHE_DIR = new File(path, "pipepipe_subtitle_cache");
        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
    }

    public static String process(String subtitleUrl, final MediaFormat format) throws IOException {
        //String ext = getExtensionFromUrl(subtitleUrl);
        String ext = "." + format;
        String md5Url = md5(subtitleUrl);
        LogUtil.logWithMessage("tree-test02", "ext=" + ext + ",md5Url=" + md5Url);

        String filename = md5Url + ext;
        LogUtil.logWithMessage("tree-test02", "filename=" + filename);
        File cacheFile = new File(CACHE_DIR, filename);

        // If file exists, return it
        if (cacheFile.exists()) {
            return "file://" + cacheFile.getAbsolutePath();
        }

        // Download and compare
        String downloadedContent = downloadText(subtitleUrl);
        //LogUtil.logWithMessage("tree-test02", "downloadedContent=" + downloadedContent);

        String finalContent = "";
        if (true == containsDuplicateTtmlEntries(downloadedContent)) {
            LogUtil.logWithMessage("tree-test02", "find duplication subtitle");
            finalContent = deduplicateTtml(downloadedContent);
        } else {
            LogUtil.logWithMessage("tree-test02", "Not find duplication subtitle");
            finalContent = parseAndDeduplicateSubtitle(downloadedContent);
        }

        // Deduplicate: if same, write once
        //String finalContent = parseAndDeduplicateSubtitle(downloadedContent);
        LogUtil.logWithMessage("tree-test02", "finalContent=" + finalContent);

        // 确保父目录存在
        File parentDir = cacheFile.getParentFile();
        if (!parentDir.exists()) {
            boolean success = parentDir.mkdirs();
            LogUtil.logWithMessage("tree-test02", "创建父目录: " + parentDir.getAbsolutePath() + " 是否成功: " + success);
        } else {
            LogUtil.logWithMessage("tree-test02", "parentDir exists: " + parentDir.getAbsolutePath());
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(cacheFile), StandardCharsets.UTF_8))) {
            writer.write(finalContent);
            LogUtil.logWithMessage("tree-test02", "成功写入字幕缓存: " + cacheFile.getAbsolutePath());
        } catch (IOException e) {
            LogUtil.logWithMessage("tree-test02", "写入字幕文件失败: " + e.getMessage());
            e.printStackTrace();
        }

        return "file://" + cacheFile.getAbsolutePath();
    }

    private static String downloadText(String urlStr) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(urlStr).openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static String parseAndDeduplicateSubtitle(String input) {
        // ⚠️ 这里只是最简单的占位去重逻辑，后续你可以自定义更复杂的规则
        // 例如 VTT/SRT 重复片段识别、相邻内容合并等
        return input.trim();
    }

    private static String getExtensionFromUrl(String url) {
        int qIndex = url.indexOf('?');
        String cleaned = (qIndex > 0) ? url.substring(0, qIndex) : url;
        int dotIndex = cleaned.lastIndexOf('.');
        return (dotIndex > 0) ? cleaned.substring(dotIndex) : ".vtt"; // 默认.vtt
    }

    // make the long url to be short
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
    }

    /**
     * 新增：传入字幕文件，检测是否有重复 TTML 条目
     */
    public static boolean containsDuplicateTtmlEntries(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return false;

        try {
            String content = readFileToString(subtitleFile);
            return containsDuplicateTtmlEntries(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 原始函数：传入 TTML 字幕内容字符串，检测是否有重复
     */
    public static boolean containsDuplicateTtmlEntries(String subtitleContent) {
        if (subtitleContent == null || subtitleContent.isEmpty()) return false;

        Pattern pattern = Pattern.compile(
                "<p[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String begin = matcher.group(1).trim();
            String end = matcher.group(2).trim();
            String content = matcher.group(3).trim().replaceAll("\\s+", " ");
            String key = begin + "|" + end + "|" + content;

            if (seen.contains(key)) return true;
            seen.add(key);
        }

        return false;
    }

    /**
     * 读取整个文件为字符串
     */
    private static String readFileToString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static String deduplicateTtmlFile(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return "";

        try {
            String content = readFileToString(subtitleFile);
            return deduplicateTtml(content);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String deduplicateTtml(String subtitleContent) {
        if (subtitleContent == null || subtitleContent.isEmpty()) return subtitleContent;

        Pattern pattern = Pattern.compile(
                "<p[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        StringBuilder result = new StringBuilder();

        int lastIndex = 0;
        while (matcher.find()) {
            result.append(subtitleContent, lastIndex, matcher.start());

            String begin = matcher.group(1).trim();
            String end = matcher.group(2).trim();
            String content = matcher.group(3).trim().replaceAll("\\s+", " ");
            String key = begin + "|" + end + "|" + content;

            if (!seen.contains(key)) {
                result.append(matcher.group(0));
                seen.add(key);
            }

            lastIndex = matcher.end();
        }

        result.append(subtitleContent.substring(lastIndex));
        return result.toString();
    }


}
