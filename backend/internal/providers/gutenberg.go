package providers

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"reader-backend/internal/models"
	"strconv"
	"strings"
	"time"
)

// Estructuras DTO privadas para mapear la respuesta específica de Gutendex
type gutendexPerson struct {
	Name string `json:"name"`
}

type gutendexBook struct {
	ID        int               `json:"id"`
	Title     string            `json:"title"`
	Authors   []gutendexPerson  `json:"authors"`
	Languages []string          `json:"languages"`
	Formats   map[string]string `json:"formats"`
}

type gutendexResponse struct {
	Count   int            `json:"count"`
	Results []gutendexBook `json:"results"`
}

// GutenbergProvider implementa la interfaz BookProvider (LSP)
type GutenbergProvider struct {
	client  *http.Client
	baseURL string
}

// NewGutenbergProvider es el constructor con inyección de dependencias (DIP)
func NewGutenbergProvider(client *http.Client) *GutenbergProvider {
	if client == nil {
		client = &http.Client{
			Timeout: 15 * time.Second,
		}
	}
	return &GutenbergProvider{
		client:  client,
		baseURL: "https://gutendex.com/books",
	}
}

// Name retorna el identificador de este proveedor
func (g *GutenbergProvider) Name() string {
	return "gutenberg"
}

// Search consulta la API externa y transforma el resultado a []models.Book
func (g *GutenbergProvider) Search(ctx context.Context, filter models.SearchFilter) ([]models.Book, error) {
	params := url.Values{}

	// Término de búsqueda
	searchTerm := filter.Query
	if filter.Author != "" {
		searchTerm = fmt.Sprintf("%s %s", searchTerm, filter.Author)
	}
	params.Set("search", strings.TrimSpace(searchTerm))

	// Idioma (por defecto "es" si no se especifica)
	lang := "es"
	if filter.Language != "" {
		lang = filter.Language
	}
	params.Set("languages", lang)

	fullURL := fmt.Sprintf("%s?%s", g.baseURL, params.Encode())

	// Creamos la petición HTTP respetando el contexto recibido
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, fullURL, nil)
	if err != nil {
		return nil, fmt.Errorf("error creando petición: %w", err)
	}
	req.Header.Set("User-Agent", "EbookAggregator/1.0 (reader-ecosystem)")

	resp, err := g.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("error conectando con Gutendex: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("servidor Gutendex respondió con código: %d", resp.StatusCode)
	}

	var rawResponse gutendexResponse
	if err := json.NewDecoder(resp.Body).Decode(&rawResponse); err != nil {
		return nil, fmt.Errorf("error decodificando respuesta JSON: %w", err)
	}

	var books []models.Book
	for _, item := range rawResponse.Results {
		// Buscamos el enlace de descarga directa en formato EPUB
		epubURL := g.extractEpubURL(item.Formats)
		if epubURL == "" {
			// Si el libro no tiene versión EPUB descargable, lo omitimos
			continue
		}

		// Mapear autores
		var authors []string
		for _, a := range item.Authors {
			authors = append(authors, a.Name)
		}

		// Extraer portada (si existe)
		coverURL := item.Formats["image/jpeg"]

		// Mapear idioma
		bookLang := lang
		if len(item.Languages) > 0 {
			bookLang = item.Languages[0]
		}

		books = append(books, models.Book{
			ID:          strconv.Itoa(item.ID),
			Title:       item.Title,
			Authors:     authors,
			Language:    bookLang,
			CoverURL:    coverURL,
			DownloadURL: epubURL,
			Extension:   "epub",
			Provider:    g.Name(),
		})

		// Si se definió un límite y ya lo alcanzamos, salimos del ciclo
		if filter.Limit > 0 && len(books) >= filter.Limit {
			break
		}
	}

	return books, nil
}

// extractEpubURL busca en el mapa de formatos la URL más adecuada para el archivo EPUB
func (g *GutenbergProvider) extractEpubURL(formats map[string]string) string {
	// Prioridad 1: Formato estándar EPUB
	if link, ok := formats["application/epub+zip"]; ok {
		return link
	}

	// Prioridad 2: Buscar cualquier formato que contenga "epub"
	for key, link := range formats {
		if strings.Contains(strings.ToLower(key), "epub") {
			return link
		}
	}

	return ""
}
