package com.stock.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Завантажує Google Таблицю як .xlsx за звичайним посиланням.
 * Без ключів і налаштувань Google: таблиця має бути доступна «Усім, хто має посилання»
 * (або опублікована: Файл → Поділитися → Опублікувати в інтернеті).
 */
@Service
public class GoogleSheets {

    private static final Pattern DOC_ID = Pattern.compile("/spreadsheets/d/([A-Za-z0-9_-]{20,})");
    private static final Pattern PUBLISHED_ID = Pattern.compile("/spreadsheets/d/e/([A-Za-z0-9_-]{20,})");
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public InputStream download(String link) {
        URI uri = exportUri(link.trim());
        HttpResponse<byte[]> res;
        try {
            res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new StockException.BadRequest("Cannot reach Google. Check the Raspberry Pi internet connection");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StockException.BadRequest("Download interrupted");
        }
        byte[] body = res.body();
        // .xlsx — це zip-архів, починається з "PK". Інакше Google повернув сторінку входу.
        boolean xlsx = body.length > 2 && body[0] == 'P' && body[1] == 'K';
        if (res.statusCode() != 200 || !xlsx)
            throw new StockException.BadRequest("Google did not return the sheet. Set sharing to: Share → \"Anyone with the link\"");
        if (body.length > MAX_BYTES) throw new StockException.BadRequest("The sheet is too large");
        return new ByteArrayInputStream(body);
    }

    static URI exportUri(String link) {
        var pub = PUBLISHED_ID.matcher(link);
        if (pub.find()) return URI.create("https://docs.google.com/spreadsheets/d/e/" + pub.group(1) + "/pub?output=xlsx");
        var doc = DOC_ID.matcher(link);
        if (doc.find()) return URI.create("https://docs.google.com/spreadsheets/d/" + doc.group(1) + "/export?format=xlsx");
        throw new StockException.BadRequest("This does not look like a Google Sheets link");
    }
}
