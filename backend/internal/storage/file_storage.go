package storage

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"reader-backend/internal/models"
	"strings"
	"time"
	"unicode"
)

type StorageService interface {
	SaveFromURL(ctx context.Context, downloadURL, title, extension string) (*models.DownloadResponse, error)
	GetBasePath() string
}

type LocalFileStorage struct {
	baseDir string
	client  *http.Client
}

func NewLocalFileStorage(baseDir string, client *http.Client) (*LocalFileStorage, error) {
	if err := os.MkdirAll(baseDir, 0755); err != nil {
		return nil, fmt.Errorf("no se pudo crear el directorio de almacenamiento '%s': %w", baseDir, err)
	}

	if client == nil {
		client = &http.Client{
			Timeout: 120 * time.Second,
		}
	}

	return &LocalFileStorage{
		baseDir: baseDir,
		client:  client,
	}, nil
}

func (s *LocalFileStorage) GetBasePath() string {
	return s.baseDir
}

func (s *LocalFileStorage) SaveFromURL(ctx context.Context, downloadURL, title, extension string) (*models.DownloadResponse, error) {
	startTime := time.Now()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, downloadURL, nil)
	if err != nil {
		return nil, fmt.Errorf("error preparando petición de descarga: %w", err)
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ReaderEcosystem/1.0")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("error al descargar el archivo: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("el servidor de descarga respondió con código HTTP %d", resp.StatusCode)
	}

	// ========================================================================
	// DETECCIÓN INTELIGENTE DE EXTENSIÓN (MIME TYPE)
	// ========================================================================
	contentType := resp.Header.Get("Content-Type")
	finalExt := extension

	switch {
	case strings.Contains(contentType, "application/pdf"):
		finalExt = "pdf"
	case strings.Contains(contentType, "application/epub+zip"):
		finalExt = "epub"
	case strings.Contains(contentType, "application/x-mobipocket-ebook") || strings.Contains(contentType, "kindle"):
		finalExt = "mobi"
	}
	// ========================================================================

	// Usamos la extensión detectada (finalExt) en lugar de la solicitada (extension)
	fileName := s.sanitizeFileName(title, finalExt)
	targetPath := filepath.Join(s.baseDir, fileName)

	out, err := os.Create(targetPath)
	if err != nil {
		return nil, fmt.Errorf("error creando archivo en disco '%s': %w", targetPath, err)
	}

	var success bool
	defer func() {
		out.Close()
		if !success {
			_ = os.Remove(targetPath)
		}
	}()

	bytesWritten, err := io.Copy(out, resp.Body)
	if err != nil {
		return nil, fmt.Errorf("error escribiendo el flujo de datos en disco: %w", err)
	}

	success = true
	duration := time.Since(startTime).Seconds()

	return &models.DownloadResponse{
		Success:      true,
		FileName:     fileName,
		FilePath:     targetPath,
		SizeBytes:    bytesWritten,
		DurationSec:  duration,
		DownloadedAt: time.Now(),
	}, nil
}

func (s *LocalFileStorage) sanitizeFileName(title, extension string) string {
	clean := strings.TrimSpace(strings.ToLower(title))
	clean = strings.ReplaceAll(clean, " ", "_")

	var builder strings.Builder
	for _, r := range clean {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' || r == '-' {
			builder.WriteRune(r)
		}
	}

	result := builder.String()
	if result == "" {
		result = fmt.Sprintf("libro_%d", time.Now().Unix())
	}

	ext := strings.TrimPrefix(strings.ToLower(extension), ".")
	if ext == "" {
		ext = "epub"
	}

	return fmt.Sprintf("%s.%s", result, ext)
}
