package handlers

import (
	"log"
	"net/http"
	"time"
)

// SetupRoutes registra los endpoints de la API y conecta los middlewares globales.
func SetupRoutes(bookHandler *BookHandler) http.Handler {
	mux := http.NewServeMux()

	// 1. Healthcheck para verificar el estado del servidor en la red local
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok","timestamp":"` + time.Now().UTC().Format(time.RFC3339) + `"}`))
	})

	// 2. Endpoints de la API v1
	mux.HandleFunc("/api/v1/books/search", bookHandler.Search)
	mux.HandleFunc("/api/v1/books/download", bookHandler.Download)

	// 3. Encadenar middlewares: Logging -> CORS -> Mux
	return corsMiddleware(loggingMiddleware(mux))
}

// corsMiddleware permite que la app móvil en tu red local consulte la API sin restricciones
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")

		// Responder de inmediato a peticiones preflight OPTIONS
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		next.ServeHTTP(w, r)
	})
}

// loggingMiddleware registra el método, la ruta y el tiempo de respuesta con consumo mínimo de CPU
func loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("➡️ [%s] %s | Tiempo: %v", r.Method, r.URL.Path, time.Since(start))
	})
}
