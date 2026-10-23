package com.stock.web;

import com.stock.service.GoogleSheets;
import com.stock.service.Importer;
import com.stock.service.StockException;
import com.stock.web.Auth.AdminOnly;
import com.stock.web.Auth.SessionUser;
import com.stock.web.Dto.ImportLink;
import com.stock.web.Dto.ImportResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final Importer importer;
    private final GoogleSheets google;

    public ImportController(Importer importer, GoogleSheets google) {
        this.importer = importer;
        this.google = google;
    }

    /** Файл .xlsx (з Excel або Google Таблиць: Файл → Завантажити → Microsoft Excel). */
    @AdminOnly
    @PostMapping("/file")
    public ImportResult file(@RequestParam("file") MultipartFile file,
                             @RequestAttribute(Auth.SESSION_KEY) SessionUser u) throws IOException {
        if (file.isEmpty()) throw new StockException.BadRequest("The file is empty");
        return importer.importXlsx(file.getInputStream(), u.username());
    }

    /** Посилання на Google Таблицю. */
    @AdminOnly
    @PostMapping("/google")
    public ImportResult google(@Valid @RequestBody ImportLink req, @RequestAttribute(Auth.SESSION_KEY) SessionUser u) {
        return importer.importXlsx(google.download(req.url()), u.username());
    }
}
