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

// StorageService define el contrato de almacenamiento (ISP / DIP).
// Cualquier handler o caso de uso dependerá de esta interfaz, no de la implementación concreta en disco.
type StorageService interface {
	SaveFromURL(ctx context.Context, downloadURL, title, extension string) (*models.DownloadResponse, error)
	GetBasePath() string
}

// LocalFileStorage implementa StorageService guardando archivos en el sistema de archivos local.
type LocalFileStorage struct {
	baseDir string
	client  *http.Client
}

// NewLocalFileStorage es el constructor con inyección de dependencias.
func NewLocalFileStorage(baseDir string, client *http.Client) (*LocalFileStorage, error) {
	// Aseguramos que el directorio de destino exista
	if err := os.MkdirAll(baseDir, 0755); err != nil {
		return nil, fmt.Errorf("no se pudo crear el directorio de almacenamiento '%s': %w", baseDir, err)
	}

	if client == nil {
		client = &http.Client{
			Timeout: 120 * time.Second, // Timeout amplio para descargas de archivos grandes
		}
	}

	return &LocalFileStorage{
		baseDir: baseDir,
		client:  client,
	}, nil
}

// GetBasePath retorna la ruta del directorio base donde Syncthing sincroniza
func (s *LocalFileStorage) GetBasePath() string {
	return s.baseDir
}

// SaveFromURL descarga el archivo en streaming directamente al disco
func (s *LocalFileStorage) SaveFromURL(ctx context.Context, downloadURL, title, extension string) (*models.DownloadResponse, error) {
	startTime := time.Now()

	// 1. Sanitizar el nombre del archivo para que sea válido en cualquier sistema operativo (Linux/Android)
	fileName := s.sanitizeFileName(title, extension)
	targetPath := filepath.Join(s.baseDir, fileName)

	// 2. Iniciar la petición HTTP respetando el contexto de cancelación
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, downloadURL, nil)
	if err != nil {
		return nil, fmt.Errorf("error preparando petición de descarga: %w", err)
	}
	req.Header.Set("User-Agent", "EbookAggregator/1.0 (reader-ecosystem)")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("error al descargar el archivo: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("el servidor de descarga respondió con código HTTP %d", resp.StatusCode)
	}

	// 3. Crear el archivo en disco
	out, err := os.Create(targetPath)
	if err != nil {
		return nil, fmt.Errorf("error creando archivo en disco '%s': %w", targetPath, err)
	}

	// Si algo falla a mitad de la descarga, cerramos y borramos el archivo incompleto
	var success bool
	defer func() {
		out.Close()
		if !success {
			_ = os.Remove(targetPath)
		}
	}()

	// 4. Streaming directo de la red al disco (Consumo de RAM constante < 2MB)
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

// sanitizeFileName elimina caracteres problemáticos y asegura que tenga la extensión correcta
func (s *LocalFileStorage) sanitizeFileName(title, extension string) string {
	// Normalizar espacios y minúsculas
	clean := strings.TrimSpace(strings.ToLower(title))
	clean = strings.ReplaceAll(clean, " ", "_")

	// Conservar solo letras, números, guiones y guiones bajos
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

	// Asegurar extensión
	ext := strings.TrimPrefix(strings.ToLower(extension), ".")
	if ext == "" {
		ext = "epub"
	}

	return fmt.Sprintf("%s.%s", result, ext)
}
