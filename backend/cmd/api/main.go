package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"reader-backend/internal/handlers"
	"reader-backend/internal/providers"
	"reader-backend/internal/storage"
)

func main() {
	// 1. Configuración por flags o variables de entorno
	port := flag.String("port", getEnv("PORT", "8080"), "Puerto del servidor HTTP")
	storagePath := flag.String("storage", getEnv("STORAGE_PATH", "./storage/downloads"), "Ruta de la carpeta para Syncthing")
	flag.Parse()

	log.Printf("🚀 Iniciando Reader Backend Server...")
	log.Printf("📁 Carpeta de almacenamiento: %s", *storagePath)

	// 2. Cliente HTTP compartido con configuración de timeouts
	httpClient := &http.Client{
		Timeout: 30 * time.Second,
	}

	// 3. Inicialización del módulo de almacenamiento en disco
	fileStorage, err := storage.NewLocalFileStorage(*storagePath, httpClient)
	if err != nil {
		log.Fatalf("❌ Error crítico inicializando almacenamiento: %v", err)
	}

	// 4. Inicialización y registro de proveedores de libros (Open/Closed Principle)
	providerManager := providers.NewManager()
	gutenberg := providers.NewGutenbergProvider(httpClient)
	providerManager.Register(gutenberg)

	// (Aquí se registrarán futuros proveedores, ej: Scraper de novelas ligeras)
	log.Printf("🔌 Proveedores registrados: [%s]", gutenberg.Name())

	// 5. Inyección de dependencias en los handlers y configuración de rutas
	bookHandler := handlers.NewBookHandler(providerManager, fileStorage)
	router := handlers.SetupRoutes(bookHandler)

	// 6. Configuración del servidor HTTP
	serverAddr := fmt.Sprintf("0.0.0.0:%s", *port)
	server := &http.Server{
		Addr:         serverAddr,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 120 * time.Second, // Tiempo suficiente para descargas en streaming
		IdleTimeout:  60 * time.Second,
	}

	// 7. Canal para escuchar señales de terminación del sistema operativo (Ctrl+C, SIGTERM)
	stopChan := make(chan os.Signal, 1)
	signal.Notify(stopChan, os.Interrupt, syscall.SIGTERM)

	// Iniciar el servidor en una goroutine (asíncrono)
	go func() {
		log.Printf("✅ Servidor escuchando en: http://%s", serverAddr)
		log.Printf("   • Healthcheck: http://%s/health", serverAddr)
		log.Printf("   • Buscador:    http://%s/api/v1/books/search?q=odisea&lang=es", serverAddr)
		log.Printf("   • Descargas:   POST http://%s/api/v1/books/download", serverAddr)

		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("❌ Error en el servidor: %v", err)
		}
	}()

	// 8. Esperar señal de apagado
	<-stopChan
	log.Println("\n🛑 Señal de apagado recibida. Cerrando servidor de forma segura...")

	// Dar 10 segundos para completar peticiones pendientes antes de forzar el cierre
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("❌ Error durante el apagado del servidor: %v", err)
	}

	log.Println("👋 Servidor detenido correctamente.")
}

// getEnv obtiene una variable de entorno o usa un valor por defecto
func getEnv(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}
