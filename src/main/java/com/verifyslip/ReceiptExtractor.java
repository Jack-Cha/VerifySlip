package com.verifyslip;

import com.google.gson.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receipt data extraction using Gemini API
 * Java version of ExtReceipt.ipynb
 */
public class ReceiptExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ReceiptExtractor.class);
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final Gson gson;

    public ReceiptExtractor() {
        this.apiKey = Config.getApiKey();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Upload PDF file to Gemini File API
     * @param filePath Path to PDF file
     * @return Uploaded file information
     */
    private JsonObject uploadFile(String filePath) throws IOException {
        logger.info("'{}' 파일을 업로드하는 중...", filePath);

        // Copy to temporary file to handle Korean filename
        Path tempPath = Paths.get(Config.TEMP_FILE_PATH);
        Files.copy(Paths.get(filePath), tempPath, StandardCopyOption.REPLACE_EXISTING);

        try {
            File tempFile = tempPath.toFile();
            long fileSize = tempFile.length();

            // Step 1: Start resumable upload
            String uploadUrl = String.format("%s/upload/v1beta/files?key=%s", GEMINI_API_BASE, apiKey);

            JsonObject metadata = new JsonObject();
            JsonObject file = new JsonObject();
            file.addProperty("display_name", tempFile.getName());
            metadata.add("file", file);

            RequestBody metadataBody = RequestBody.create(
                gson.toJson(metadata),
                MediaType.get("application/json; charset=utf-8")
            );

            Request initRequest = new Request.Builder()
                    .url(uploadUrl)
                    .post(metadataBody)
                    .addHeader("X-Goog-Upload-Protocol", "resumable")
                    .addHeader("X-Goog-Upload-Command", "start")
                    .addHeader("X-Goog-Upload-Header-Content-Length", String.valueOf(fileSize))
                    .addHeader("X-Goog-Upload-Header-Content-Type", "application/pdf")
                    .build();

            String uploadUri;
            try (Response initResponse = client.newCall(initRequest).execute()) {
                if (!initResponse.isSuccessful()) {
                    String errorBody = initResponse.body() != null ? initResponse.body().string() : "";
                    throw new IOException("업로드 시작 실패: " + initResponse.code() + " - " + errorBody);
                }
                uploadUri = initResponse.header("X-Goog-Upload-URL");
                if (uploadUri == null) {
                    throw new IOException("업로드 URL을 받지 못했습니다");
                }
            }

            // Step 2: Upload file content
            byte[] fileBytes = Files.readAllBytes(tempPath);
            RequestBody fileBody = RequestBody.create(fileBytes, MediaType.get("application/pdf"));

            Request uploadRequest = new Request.Builder()
                    .url(uploadUri)
                    .put(fileBody)
                    .addHeader("Content-Length", String.valueOf(fileSize))
                    .addHeader("X-Goog-Upload-Offset", "0")
                    .addHeader("X-Goog-Upload-Command", "upload, finalize")
                    .build();

            try (Response uploadResponse = client.newCall(uploadRequest).execute()) {
                if (!uploadResponse.isSuccessful()) {
                    String errorBody = uploadResponse.body() != null ? uploadResponse.body().string() : "";
                    throw new IOException("파일 업로드 실패: " + uploadResponse.code() + " - " + errorBody);
                }

                String responseBody = uploadResponse.body().string();
                JsonObject fileInfo = gson.fromJson(responseBody, JsonObject.class);
                JsonObject uploadedFile = fileInfo.getAsJsonObject("file");

                String fileName = uploadedFile.get("name").getAsString();
                String mimeType = uploadedFile.get("mimeType").getAsString();

                logger.info("업로드 완료. 파일 이름: {}", fileName);
                logger.info("MIME 타입: {}", mimeType);

                return uploadedFile;
            }
        } finally {
            // Delete temporary file
            Files.deleteIfExists(tempPath);
        }
    }

    /**
     * Generate content using Gemini API
     * @param fileUri Uploaded file URI
     * @param prompt Extraction prompt
     * @return API response text
     */
    private String generateContent(String fileUri, String prompt) throws IOException {
        String generateUrl = String.format(
            "%s/v1beta/models/%s:generateContent?key=%s",
            GEMINI_API_BASE,
            Config.MODEL_NAME,
            apiKey
        );

        // Build request JSON
        JsonObject requestJson = new JsonObject();
        JsonArray contents = new JsonArray();

        // Add file part
        JsonObject filePart = new JsonObject();
        JsonObject fileData = new JsonObject();
        fileData.addProperty("mimeType", "application/pdf");
        fileData.addProperty("fileUri", fileUri);
        filePart.add("fileData", fileData);

        // Add text part
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);

        // Add parts to content
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        parts.add(filePart);
        parts.add(textPart);
        content.add("parts", parts);
        contents.add(content);

        requestJson.add("contents", contents);

        RequestBody body = RequestBody.create(
            gson.toJson(requestJson),
            JSON_MEDIA_TYPE
        );

        Request request = new Request.Builder()
                .url(generateUrl)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Content generation 실패: " + response.code() + " - " + response.message());
            }

            String responseBody = response.body().string();
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

            // Extract text from response
            JsonArray candidates = responseJson.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new IOException("응답에 candidates가 없습니다.");
            }

            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject contentObj = candidate.getAsJsonObject("content");
            JsonArray responseParts = contentObj.getAsJsonArray("parts");

            if (responseParts == null || responseParts.isEmpty()) {
                throw new IOException("응답에 parts가 없습니다.");
            }

            return responseParts.get(0).getAsJsonObject().get("text").getAsString();
        }
    }

    /**
     * Clean markdown code blocks from response
     * @param raw Raw response text
     * @return Cleaned JSON string
     */
    private String cleanJsonResponse(String raw) {
        // Remove markdown code blocks (```json ... ``` or ``` ... ```)
        Pattern pattern = Pattern.compile("^```(?:json)?\\s*(.*)\\s*```$",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(raw.trim());

        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return raw.trim();
    }

    /**
     * Save JSON to file
     * @param jsonElement JSON data
     * @param outputPath Output file path
     */
    private void saveJsonToFile(JsonElement jsonElement, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);

        // Create parent directories if they don't exist
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Write JSON to file
        String jsonString = gson.toJson(jsonElement);
        Files.writeString(path, jsonString);

        logger.info("\n결과가 '{}' 파일로 저장되었습니다.", outputPath);
    }

    /**
     * Format elapsed time
     * @param duration Duration
     * @return Formatted time string (HH:MM:SS.mmm)
     */
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    /**
     * Extract receipt data from PDF
     */
    public void extract() {
        Instant startTime = Instant.now();

        try {
            // 1. Upload PDF file
            JsonObject uploadedFile = uploadFile(Config.PDF_PATH);
            String fileUri = uploadedFile.get("uri").getAsString();

            // 2. Generate content with Gemini API
            String responseText = generateContent(fileUri, Config.EXTRACTION_PROMPT);

            // 3. Clean and parse JSON response
            String cleanJson = cleanJsonResponse(responseText);
            JsonElement parsedJson = gson.fromJson(cleanJson, JsonElement.class);

            // 4. Save to JSON file
            saveJsonToFile(parsedJson, Config.JSON_PATH);

            // Print count if it's a JSON array or object
            if (parsedJson.isJsonArray()) {
                logger.info("추출된 항목 수: {}", parsedJson.getAsJsonArray().size());
            } else if (parsedJson.isJsonObject()) {
                logger.info("추출된 필드 수: {}", parsedJson.getAsJsonObject().size());
            }

            // Print execution time
            Instant endTime = Instant.now();
            Duration elapsed = Duration.between(startTime, endTime);
            logger.info("실행 시간: {}", formatDuration(elapsed));

        } catch (Exception e) {
            logger.error("오류 발생: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        ReceiptExtractor extractor = new ReceiptExtractor();
        extractor.extract();
    }
}
