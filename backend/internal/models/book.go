package models

import "time"

// Book representa la entidad unificada de un libro en el sistema.
// Estandariza la información sin importar la fuente original.
type Book struct {
	ID          string   `json:"id"`
	Title       string   `json:"title"`
	Authors     []string `json:"authors"`
	Language    string   `json:"language"`
	CoverURL    string   `json:"cover_url,omitempty"`
	DownloadURL string   `json:"download_url"`
	Extension   string   `json:"extension"` // epub, pdf, mobi
	Provider    string   `json:"provider"`  // gutenberg, openlibrary, ln_scraper, etc.
}

// SearchFilter encapsula los criterios de búsqueda enviados por el cliente móvil.
type SearchFilter struct {
	Query    string `json:"query"`
	Author   string `json:"author,omitempty"`
	Language string `json:"language,omitempty"` // "es", "en", "ja"
	Limit    int    `json:"limit,omitempty"`
}

// SearchResponse es la respuesta JSON unificada que recibirá la app de Android.
type SearchResponse struct {
	Total  int    `json:"total"`
	Books  []Book `json:"books"`
	TookMs int64  `json:"took_ms"`
}

// DownloadRequest representa el payload que envía la app para solicitar la descarga al servidor.
type DownloadRequest struct {
	BookID      string `json:"book_id"`
	Title       string `json:"title"`
	DownloadURL string `json:"download_url"`
	Extension   string `json:"extension"`
}

// DownloadResponse detalla el resultado de la descarga guardada en la carpeta de Syncthing.
type DownloadResponse struct {
	Success      bool      `json:"success"`
	FileName     string    `json:"file_name"`
	FilePath     string    `json:"file_path"`
	SizeBytes    int64     `json:"size_bytes"`
	DurationSec  float64   `json:"duration_sec"`
	DownloadedAt time.Time `json:"downloaded_at"`
}
