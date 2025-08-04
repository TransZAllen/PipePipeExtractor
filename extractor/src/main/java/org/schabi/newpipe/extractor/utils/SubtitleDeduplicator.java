package org.schabi.newpipe.extractor.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.utils.LogUtil;

/**
 * SubtitleDeduplicator.java
 *
 * This file is responsible for checking if the subtitles
 * contain any duplicate entries.
 * 1) If duplicates are found, it performs the following steps:
 *    downloads the subtitle, deduplicates it,
 *    and stores it locally.
 * 2) If no duplicates are found, no action is taken.
 *
 * Core Functions:
 * - checkAndDeduplicate(): Checks for duplicate subtitles
 *   and handles downloading, deduplication, and local storage.
 */

public class SubtitleDeduplicator {
    private static String subCacheDir = "subtitle_cache";

    private static File CACHE_DIR = null;
    static {
        setCacheDirPathDefault();
    }

    public static void setCacheDirPath(String path) {
        CACHE_DIR = new File(path, subCacheDir);
        if (false == CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }

    // e.g. //data/user/0/***/cache/subtitle_cache
    public static void setCacheDirPathDefault() {
        File defaultFile = new File(System.getProperty("java.io.tmpdir"));
        String defaultPath = defaultFile.getAbsolutePath();

        setCacheDirPath(defaultPath);
    }

    // e.g. //storage/emulated/0/Android/data/***/cache/subtitle_cache
    public static void setCacheDirPathNotDefault(String path) {
        if (true == stringIsNullOrEmpty(path)) {
            return;
        }

        setCacheDirPath(path);
    }

    public static String checkAndDeduplicate(final String remoteSubtitleUrl,
                                            final MediaFormat format) {
        if (false == isItDuplicatedSubtitle(remoteSubtitleUrl)) {
            return remoteSubtitleUrl;
        }

        String localSubtitleUrl = deduplicateSubtitleThenStoreToCachefile(
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

    public static boolean isItDuplicatedSubtitle(String remoteSubtitleUrl) {
        String downloadedContent = downloadRemoteText(remoteSubtitleUrl);

        if (true == containsDuplicatedEntries(downloadedContent)) {
            LogUtil.logWithMessage("tree-test02", "find duplication subtitle");
            return true;
        } else {
            LogUtil.logWithMessage("tree-test02", "Not find duplication subtitle");
            return false;
        }
    }

    public static boolean containsDuplicateTtmlEntries(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) return false;

        try {
            String content = readFileToString(subtitleFile);
            return containsDuplicatedEntries(content);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean containsDuplicatedEntries(String subtitleContent) {
        if (true == stringIsNullOrEmpty(subtitleContent)) {
            return false;
        }

        Matcher matcher = getTtmlMatcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String key = getSubtitleKeyOfTtml(matcher);

            if (seen.contains(key)) {
                return true;
            }
            seen.add(key);
        }

        return false;
    }

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
            return deduplicateContent(content);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String deduplicateContent(String subtitleContent) {
        if (true == stringIsNullOrEmpty(subtitleContent)) {
            return subtitleContent;
        }

        Matcher matcher = getTtmlMatcher(subtitleContent);

        Set<String> seen = new HashSet<>();
        StringBuilder result = new StringBuilder();

        int lastIndex = 0;
        while (matcher.find()) {
            result.append(subtitleContent, lastIndex, matcher.start());

            String key = getSubtitleKeyOfTtml(matcher);

            if (!seen.contains(key)) {
                result.append(matcher.group(0));
                seen.add(key);
            }

            lastIndex = matcher.end();
        }

        result.append(subtitleContent.substring(lastIndex));
        return result.toString();
    }

    private static boolean stringIsNullOrEmpty(String inputString) {
        if (null == inputString) {
            return true;
        }

        if (true == inputString.isEmpty()) {
            return true;
        }

        return false;
    }

    private static Pattern defineTtmlSubtitlePattern() {
        return Pattern.compile(
            "<p[^>]*begin=\"([^\"]+)\"[^>]*end=\"([^\"]+)\"[^>]*>(.*?)</p>",
            Pattern.DOTALL
        );
    }

    private static Matcher getTtmlMatcher(String subtitleContent) {
        Pattern pattern = defineTtmlSubtitlePattern();
        return pattern.matcher(subtitleContent);
    }

    private static String getSubtitleKeyOfTtml(Matcher matcher) {
        String begin = matcher.group(1).trim();
        String end = matcher.group(2).trim();
        String content = matcher.group(3).trim().replaceAll("\\s+", " ");
        String key = begin + "|" + end + "|" + content;
        return key;
    }

    public static String deduplicateSubtitleThenStoreToCachefile(
                                                final String subtitleUrl,
                                                final MediaFormat format) {
        File cacheFile = getDeduplicatedCachefileName(subtitleUrl, format);

        String cacheFilePathForExoplayer = "file://" + cacheFile.getAbsolutePath();

        int deduplicatedBefore = hasTheSubtitleBeenDeduplicatedBefore(cacheFile);
        if (0 == deduplicatedBefore) {
            return cacheFilePathForExoplayer;
        }

        if (false == ensureItsParentDirExist(cacheFile)) {
            LogUtil.logWithMessage("tree-test02", cacheFile.getAbsolutePath() + ": its parent dir Not exist!");
            return null;
        }

        String downloadedContent = downloadRemoteText(subtitleUrl);

        String finalContent = deduplicateContent(downloadedContent);

        if (null == writeDeduplicatedContentToCachefile(finalContent, cacheFile)) {
            return cacheFilePathForExoplayer;
        } else {
            LogUtil.logWithMessage("tree-test02", "fail to write cachefile!");
            return null;
        }
    }

    private static String computeShorterFilename(String subtitleUrl,
                                                final MediaFormat format,
                                                String tag0) {
        String md5Url = md5(subtitleUrl);
        String baseName = md5Url;

        //String fileExtension = "." + format;
        //String filename = baseName + fileExtension;
        String key = "lang";
        String languageCode = extractLanguageCode(subtitleUrl, key);

        String autoTranslateLanguage = checkAutoTranslateLanguage(subtitleUrl);
        if (null != autoTranslateLanguage) {
            languageCode = autoTranslateLanguage;
        }

        String filename = baseName
                        + "-" + tag0
                        + "&" + key + "="
                        + languageCode
                        + "&fmt="
                        + format.getSuffix();

        return filename;
    }

    private static String checkAutoTranslateLanguage(String subtitleUrl) {
        String key_autoTranslate = "tlang";
        String language_autoTranslate = extractLanguageCode(subtitleUrl, key_autoTranslate);

        if(true == stringIsNullOrEmpty(language_autoTranslate)) {
            return null;
        } else {
            return language_autoTranslate;
        }
    }

    private static String extractLanguageCode(String remoteSubtitleUrl, String key) {
        String languageCode = null;

        try {
            URL url = Utils.stringToURL(remoteSubtitleUrl);

            String value = Utils.getQueryValue(url, key);

            languageCode = value;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            languageCode = "INVALID_URL";
        } catch (Exception e) {
            e.printStackTrace();
            languageCode = "UNKNOWN_ERROR";
        }

        return languageCode;
    }

    private static File getDeduplicatedCachefileName(String subtitleUrl, MediaFormat format) {
        String tag0 = "deduplicated";

        File DeduplicatedFileName = getCachefileName(subtitleUrl,format,tag0);

        return DeduplicatedFileName;
    }

    private static File getCachefileName(String subtitleUrl,
                                        MediaFormat format,
                                        String tag0) {
        String cachefilename = computeShorterFilename(subtitleUrl, format, tag0);

        File tempCacheFile = new File(CACHE_DIR, cachefilename);

        return tempCacheFile;
    }

    // 0: it has been deduplicated bofore.
    private static int hasTheSubtitleBeenDeduplicatedBefore(File tempCacheFile) {
        if (tempCacheFile.exists()) {
            if (true == isFileEmpty(tempCacheFile)) {
                return 1; // error
            } else {
                return 0;
            }
        } else {
            return 2;
        }
    }

    private static boolean isFileEmpty(File file) {
        if(0 == file.length()) {
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
        String result = writeContentToFile(subtitleContent, tempCacheFile);
        return result;
    }

    private static String writeContentToFile(String content, File tempFile) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            writer.write(content);
            LogUtil.logWithMessage("tree-test02", "succeed to write the cache file: " + tempFile.getAbsolutePath());
            return null;//ok
        } catch (IOException e) {
            //LogUtil.logWithMessage("tree-test02", "fail to write the cache file: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = e.getMessage();
            return errorMessage;
        }
    }

}
