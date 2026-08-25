package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"path/filepath"
	"reader-backend/internal/models"
	"reader-backend/internal/providers"
	"reader-backend/internal/storage"
	"strconv"
	"strings"
	"time"
)

// BookHandler maneja las peticiones HTTP relacionadas con libros.
type BookHandler struct {
	providerManager *providers.Manager
	storageService  storage.StorageService
}

// NewBookHandler es el constructor con inyección de dependencias.
func NewBookHandler(pm *providers.Manager, storage storage.StorageService) *BookHandler {
	return &BookHandler{
		providerManager: pm,
		storageService:  storage,
	}
}

// Search maneja: GET /api/v1/books/search?q=odisea&lang=es&author=homero&limit=10
func (h *BookHandler) Search(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		h.writeError(w, http.StatusMethodNotAllowed, "método no permitido, use GET")
		return
	}

	query := strings.TrimSpace(r.URL.Query().Get("q"))
	if query == "" {
		h.writeError(w, http.StatusBadRequest, "el parámetro de búsqueda 'q' es obligatorio")
		return
	}

	author := strings.TrimSpace(r.URL.Query().Get("author"))
	lang := strings.TrimSpace(r.URL.Query().Get("lang"))
	if lang == "" {
		lang = "es"
	}

	limit := 10
	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if parsedLimit, err := strconv.Atoi(limitStr); err == nil && parsedLimit > 0 {
			limit = parsedLimit
		}
	}

	filter := models.SearchFilter{
		Query:    query,
		Author:   author,
		Language: lang,
		Limit:    limit,
	}

	startTime := time.Now()
	books, err := h.providerManager.SearchAll(r.Context(), filter)
	if err != nil {
		h.writeError(w, http.StatusInternalServerError, "error consultando fuentes de libros: "+err.Error())
		return
	}

	tookMs := time.Since(startTime).Milliseconds()

	response := models.SearchResponse{
		Total:  len(books),
		Books:  books,
		TookMs: tookMs,
	}

	h.writeJSON(w, http.StatusOK, response)
}

// Download maneja: POST /api/v1/books/download
func (h *BookHandler) Download(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		h.writeError(w, http.StatusMethodNotAllowed, "método no permitido, use POST")
		return
	}

	var req models.DownloadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.writeError(w, http.StatusBadRequest, "cuerpo JSON inválido: "+err.Error())
		return
	}

	req.DownloadURL = strings.TrimSpace(req.DownloadURL)
	req.Title = strings.TrimSpace(req.Title)

	if req.DownloadURL == "" || req.Title == "" {
		h.writeError(w, http.StatusBadRequest, "los campos 'download_url' y 'title' son obligatorios")
		return
	}

	if req.Extension == "" {
		req.Extension = "epub"
	}

	// ========================================================================
	// LÓGICA DE RUTEO DE DESCARGA (BYPASS & SCRAPING)
	// ========================================================================

	// CASO 1: Es un enlace de Google Drive (Requiere Bypass de Cookies/Tokens)
	if strings.Contains(req.DownloadURL, "drive.google.com") {
		bypasser := providers.NewDriveBypasser()
		realURL, err := bypasser.GetDirectDownloadLink(r.Context(), req.DownloadURL)
		if err != nil {
			h.writeError(w, http.StatusInternalServerError, "Error saltando protección de Google Drive: "+err.Error())
			return
		}
		// Actualizamos la URL por la URL directa de descarga
		req.DownloadURL = realURL
	}

	// CASO 2: Es una Novela Ligera (Requiere Scraping + Compilación de EPUB)
	// Identificamos si es una novela por la URL o el proveedor
	if strings.Contains(req.DownloadURL, "novela") || strings.Contains(req.DownloadURL, "manga") {
		lnProvider, err := h.providerManager.Get("ln_spanish")
		if err == nil {
			if scraper, ok := lnProvider.(*providers.LightNovelProvider); ok {
				// Sanitizamos el nombre para el archivo final
				fileName := fmt.Sprintf("%s.%s", strings.ReplaceAll(strings.ToLower(req.Title), " ", "_"), req.Extension)
				targetPath := filepath.Join(h.storageService.GetBasePath(), fileName)

				// El scraper descarga capítulos y compila el EPUB directamente al disco
				err := scraper.ScrapeAndBuildEpub(r.Context(), req.DownloadURL, targetPath, 100) // Max 100 caps
				if err != nil {
					h.writeError(w, http.StatusInternalServerError, "Error compilando novela ligera: "+err.Error())
					return
				}

				h.writeJSON(w, http.StatusCreated, models.DownloadResponse{
					Success:      true,
					FileName:     fileName,
					FilePath:     targetPath,
					DownloadedAt: time.Now(),
				})
				return
			}
		}
	}

	// CASO 3: Descarga Estándar (Streaming directo a disco)
	// Este camino se toma si es un link directo, un link de Gutenberg, o un link de Drive ya procesado
	result, err := h.storageService.SaveFromURL(r.Context(), req.DownloadURL, req.Title, req.Extension)
	if err != nil {
		h.writeError(w, http.StatusInternalServerError, "error al guardar el archivo: "+err.Error())
		return
	}

	h.writeJSON(w, http.StatusCreated, result)
}

// writeJSON es un helper para serializar respuestas exitosas
func (h *BookHandler) writeJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

// writeError es un helper para estandarizar respuestas de error
func (h *BookHandler) writeError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": false,
		"error":   message,
		"status":  status,
	})
}
