package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/chromedp/cdproto/network"
	"github.com/chromedp/chromedp"
)

func main() {
	// 1. Configuración de Chromium visible (para navegar manualmente)
	opts := append(chromedp.DefaultExecAllocatorOptions[:],
		chromedp.Flag("headless", false),
		chromedp.Flag("disable-gpu", true),
		chromedp.Flag("no-default-browser-check", true),
	)

	allocCtx, cancelAlloc := chromedp.NewExecAllocator(context.Background(), opts...)
	defer cancelAlloc()

	ctx, cancelCtx := chromedp.NewContext(allocCtx)
	defer cancelCtx()

	// 2. Archivo donde guardaremos el log del recorrido
	logFile, err := os.OpenFile("traffic_log.txt", os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0666)
	if err != nil {
		log.Fatalf("❌ No se pudo crear el archivo de log: %v", err)
	}
	defer logFile.Close()

	fmt.Println("🚀 ========================================================")
	fmt.Println("🌐 GRABADOR DE TRÁFICO Y REDIRECCIONES (Go 1.22 + Chromium)")
	fmt.Println("============================================================")
	fmt.Println("👉 Navega en la ventana de Chromium hasta la descarga del libro.")
	fmt.Println("📝 Todo el tráfico se está guardando en 'traffic_log.txt'.")
	fmt.Println("🛑 Presiona Ctrl+C en esta terminal cuando empiece la descarga.")
	fmt.Println("============================================================")

	// 3. Escuchar cada petición y respuesta de la red
	chromedp.ListenTarget(ctx, func(ev interface{}) {
		switch e := ev.(type) {
		case *network.EventRequestWillBeSent:
			timestamp := time.Now().Format("15:04:05.000")
			logEntry := fmt.Sprintf("[%s] REQ -> ID: %s | Method: %s | URL: %s\n",
				timestamp, e.RequestID, e.Request.Method, e.Request.URL)

			fmt.Print(logEntry)
			_, _ = logFile.WriteString(logEntry)

		case *network.EventResponseReceived:
			timestamp := time.Now().Format("15:04:05.000")
			logEntry := fmt.Sprintf("[%s] RES <- ID: %s | Status: %d | URL: %s | MIME: %s\n",
				timestamp, e.RequestID, e.Response.Status, e.Response.URL, e.Response.MimeType)

			fmt.Print(logEntry)
			_, _ = logFile.WriteString(logEntry)
		}
	})

	// 4. Iniciar Chromium y habilitar la captura de eventos de red
	if err := chromedp.Run(ctx,
		network.Enable(),
		chromedp.Navigate("https://google.com"),
	); err != nil {
		log.Fatalf("❌ Error iniciando Chromium: %v", err)
	}

	// 5. Esperar Ctrl+C para terminar de forma limpia
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	<-sigChan

	fmt.Println("\n💾 Grabación finalizada. Revisa el archivo 'traffic_log.txt'.")
}
