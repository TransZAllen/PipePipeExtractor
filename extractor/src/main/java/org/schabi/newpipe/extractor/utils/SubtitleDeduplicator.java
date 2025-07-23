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
    private static String subCacheDir = "pipepipe_subtitle_cache";

    private static File CACHE_DIR = new File(System.getProperty("java.io.tmpdir"), subCacheDir);

    static {
        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
    }

    public static void setCacheDirPath(String path) {
        CACHE_DIR = new File(path, subCacheDir);
        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
    }

    public static String checkAndDeduplicate(final String remoteSubtitleUrl,
                                            final MediaFormat format) {
        if (false == isThereDuplicatedSubtitle(remoteSubtitleUrl)) {
            return remoteSubtitleUrl;
        }

        String localSubtitleUrl = deduplicateSubtitleThenStoreItToCachefile(
                                                                remoteSubtitleUrl,
                                                                format);
        if (null == localSubtitleUrl) {
            return remoteSubtitleUrl;
        }

        return localSubtitleUrl;
    }

    /**
     * Downloads plain text content from a remote HTTP(S) URL.
     * This method does not support local file paths or 'file://' URLs.
     *
     * @param urlStr the full HTTP or HTTPS URL to download from
     * @return the content as a String, or null if download fails
     */
    private static String downloadRemoteText(String urlStr) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new URL(urlStr).openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            LogUtil.logWithMessage("SubtitleDownloader", "Failed to download subtitle: " + e.getMessage());
            return null;
        }
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

    //
    public static boolean isThereDuplicatedSubtitle(String remoteSubtitleUrl) {
        String downloadedContent = downloadRemoteText(remoteSubtitleUrl);
        //LogUtil.logWithMessage("tree-test02", "downloadedContent=" + downloadedContent);

        if (true == containsDuplicateTtmlEntries(downloadedContent)) {
            LogUtil.logWithMessage("tree-test02", "find duplication subtitle");
            return true;
        } else {
            LogUtil.logWithMessage("tree-test02", "Not find duplication subtitle");
            return false;
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
        if (subtitleContent == null || subtitleContent.isEmpty()) {
            return false;
        }

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

            if (seen.contains(key)) {
                return true;
            }
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

    public static String deduplicateSubtitleThenStoreItToCachefile(
                                                final String subtitleUrl,
                                                final MediaFormat format) {
        File cacheFile = getCachefileName(subtitleUrl, format);

        String cacheFilePathForExoplayer = "file://" + cacheFile.getAbsolutePath();

        if (true == doesTheSubtitleEverDeduplicated(cacheFile)) {
            return cacheFilePathForExoplayer;
        }

        if (false == ensureItsParentDirExist(cacheFile)) {
            LogUtil.logWithMessage("tree-test02", cacheFile.getAbsolutePath() + ": its parent dir Not exist!");
            return null;
        }

        String downloadedContent = downloadRemoteText(subtitleUrl);

        String finalContent = deduplicateTtml(downloadedContent);

        if (null == writeDeduplicatedContentToCachefile(finalContent, cacheFile)) {
            return cacheFilePathForExoplayer;
        } else {
            LogUtil.logWithMessage("tree-test02", "fail to write cachefile!");
            return null;
        }
    }

    private static String computeShorterFilename(String subtitleUrl, final MediaFormat format) {
        String fileExtension = "." + format;

        String md5Url = md5(subtitleUrl);
        String baseName = md5Url;

        String filename = baseName + fileExtension;

        return filename;
    }

    private static File getCachefileName(String subtitleUrl, MediaFormat format) {
        String cachefilename = computeShorterFilename(subtitleUrl, format);
        LogUtil.logWithMessage("tree-test02", "cachefilename=" + cachefilename);

        File tempCacheFile = new File(CACHE_DIR, cachefilename);

        return tempCacheFile;
    }

    private static boolean doesTheSubtitleEverDeduplicated(File tempCacheFile) {
        if (tempCacheFile.exists()) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean ensureItsParentDirExist(File tempCacheFile) {
        File parentDir = tempCacheFile.getParentFile();

        if (parentDir.exists()) {
            return true;
        } else {
            boolean success = parentDir.mkdirs();
            if (true == success) {
                return true;
            } else {
                return false;
            }
        }
    }

    private static String writeDeduplicatedContentToCachefile(
                                                String subtitleContent,
                                                File tempCacheFile) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempCacheFile), StandardCharsets.UTF_8))) {
            writer.write(subtitleContent);
            LogUtil.logWithMessage("tree-test02", "succeed to write the cache file: " + tempCacheFile.getAbsolutePath());
            return null;//ok
        } catch (IOException e) {
            //LogUtil.logWithMessage("tree-test02", "fail to write the cache file: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = e.getMessage();
            return errorMessage;
        }
    }

}
