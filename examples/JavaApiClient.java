import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class JavaApiClient {
    private static final String BASE_URL = "http://127.0.0.1:8000";
    private final HttpClient client = HttpClient.newHttpClient();

    public String createSession(String title) throws IOException, InterruptedException {
        String json = "{\"title\":\"" + escapeJson(title) + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/sessions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    public String chat(String sessionId, String question) throws IOException, InterruptedException {
        String json = "{"
                + "\"session_id\":\"" + escapeJson(sessionId) + "\","
                + "\"question\":\"" + escapeJson(question) + "\""
                + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        return send(request);
    }

    public String uploadDocument(Path filePath, String subject, String chapter)
            throws IOException, InterruptedException {
        String boundary = "----JavaBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, filePath, subject, chapter);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/documents"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return send(request);
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("API error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static byte[] buildMultipartBody(
            String boundary,
            Path filePath,
            String subject,
            String chapter
    ) throws IOException {
        String filename = filePath.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(filePath);
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"subject\"\r\n\r\n"
                + subject + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"chapter\"\r\n\r\n"
                + chapter + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";

        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];
        System.arraycopy(prefixBytes, 0, body, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, body, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, body, prefixBytes.length + fileBytes.length, suffixBytes.length);
        return body;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

