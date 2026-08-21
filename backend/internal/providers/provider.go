package providers

import (
	"context"
	"fmt"
	"reader-backend/internal/models"
	"sync"
)

// BookProvider define el contrato único que debe implementar cualquier fuente de libros.
// Cumple con el Principio de Segregación de Interfaces (ISP) al mantenerse conciso y específico.
type BookProvider interface {
	// Name retorna el identificador único del proveedor (ej: "gutenberg", "ln_blog").
	Name() string

	// Search busca libros en la fuente externa y los transforma a la entidad Book unificada.
	Search(ctx context.Context, filter models.SearchFilter) ([]models.Book, error)
}

// Manager coordina y administra los diferentes proveedores de libros registrados.
// Cumple con el Principio Abierto/Cerrado (OCP): podemos registrar nuevos proveedores
// sin modificar la lógica interna de búsqueda.
type Manager struct {
	mu        sync.RWMutex
	providers map[string]BookProvider
}

// NewManager crea una nueva instancia del gestor de proveedores.
func NewManager() *Manager {
	return &Manager{
		providers: make(map[string]BookProvider),
	}
}

// Register añade un nuevo proveedor al gestor.
func (m *Manager) Register(provider BookProvider) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.providers[provider.Name()] = provider
}

// Get obtiene un proveedor específico por su nombre.
func (m *Manager) Get(name string) (BookProvider, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	p, exists := m.providers[name]
	if !exists {
		return nil, fmt.Errorf("el proveedor '%s' no está registrado", name)
	}
	return p, nil
}

// SearchAll ejecuta la búsqueda en todos los proveedores registrados de forma concurrente.
func (m *Manager) SearchAll(ctx context.Context, filter models.SearchFilter) ([]models.Book, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	var wg sync.WaitGroup
	resultsChan := make(chan []models.Book, len(m.providers))
	errChan := make(chan error, len(m.providers))

	for _, provider := range m.providers {
		wg.Add(1)
		go func(p BookProvider) {
			defer wg.Done()
			books, err := p.Search(ctx, filter)
			if err != nil {
				errChan <- fmt.Errorf("[%s] error: %w", p.Name(), err)
				return
			}
			resultsChan <- books
		}(provider)
	}

	wg.Wait()
	close(resultsChan)
	close(errChan)

	var allBooks []models.Book
	for books := range resultsChan {
		allBooks = append(allBooks, books...)
	}

	return allBooks, nil
}
