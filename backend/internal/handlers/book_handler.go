package handlers

import (
	"encoding/json"
	"net/http"
	"reader-backend/internal/models"
	"reader-backend/internal/providers"
	"reader-backend/internal/storage"
	"strconv"
	"strings"
	"time"
)

// BookHandler maneja las peticiones HTTP relacionadas con libros.
// Cumple con DIP: depende de abstracciones inyectadas en su constructor.
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
		lang = "es" // Español por defecto
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
	// Busca concurrentemente en todos los proveedores registrados
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

	// Validaciones básicas
	req.DownloadURL = strings.TrimSpace(req.DownloadURL)
	req.Title = strings.TrimSpace(req.Title)

	if req.DownloadURL == "" || req.Title == "" {
		h.writeError(w, http.StatusBadRequest, "los campos 'download_url' y 'title' son obligatorios")
		return
	}

	if req.Extension == "" {
		req.Extension = "epub"
	}

	// Descarga en streaming hacia la carpeta sincronizada con Syncthing
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
