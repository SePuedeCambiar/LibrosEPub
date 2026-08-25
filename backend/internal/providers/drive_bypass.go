package providers

import (
	"context"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/chromedp/chromedp"
	"github.com/chromedp/cdproto/network"
)

type DriveBypasser struct {
	// Aquí podrías añadir configuraciones de proxy si Google empieza a banear la IP del Celeron
}

func NewDriveBypasser() *DriveBypasser {
	return &DriveBypasser{}
}

// GetDirectDownloadLink entra a la URL de Drive y extrae el link de descarga real saltando advertencias
func (db *DriveBypasser) GetDirectDownloadLink(ctx context.Context, driveURL string) (string, error) {
	// 1. Configurar el navegador en modo invisible (Headless)
	opts := append(chromedp.DefaultExecAllocatorOptions[:],
		chromedp.Flag("headless", true),
		chromedp.Flag("disable-gpu", true),
		chromedp.Flag("no-sandbox", true),
	)

	allocCtx, cancelAlloc := chromedp.NewExecAllocator(context.Background(), opts...)
	defer cancelAlloc()

	// Creamos un contexto con timeout para no dejar procesos zombies en el Celeron
	taskCtx, cancelTask := chromedp.NewContext(allocCtx)
	defer cancelTask()

	// Canal para recibir la URL final
	finalURLChan := make(chan string, 1)

	// 2. Escuchar el tráfico de red para atrapar el link de descarga
	chromedp.ListenTarget(taskCtx, func(ev interface{}) {
		if e, ok := ev.(*network.EventRequestWillBeSent); ok {
			// Buscamos URLs que contengan 'uc?export=download' o 'confirm='
			if strings.Contains(e.Request.URL, "uc?export=download") || strings.Contains(e.Request.URL, "confirm=") {
				finalURLChan <- e.Request.URL
			}
		}
	})

	// 3. Ejecutar el flujo de navegación
	go func() {
		err := chromedp.Run(taskCtx,
			network.Enable(),
			chromedp.Navigate(driveURL),
			// Esperamos a que cargue la página y buscamos el botón de confirmación
			// El selector de Google Drive para "Descargar de todos modos" suele variar, 
			// pero buscamos el texto en el cuerpo o el botón de descarga.
			chromedp.Sleep(2*time.Second), 
			// Intentamos hacer clic en cualquier botón que diga "Descargar" o "Download"
			chromedp.Click(`//span[contains(text(), 'Descargar') or contains(text(), 'Download')]`, 
				chromedp.ByQuery),
		)
		if err != nil {
			log.Printf("⚠️ Aviso: No se encontró botón de confirmación, puede que el link sea directo: %v", err)
		}
	}()

	// 4. Esperar la URL o dar timeout
	select {
	case url := <-finalURLChan:
		return url, nil
	case <-time.After(15 * time.Second):
		// Si no hay redirección, intentamos convertir la URL /view a /uc manualmente
		return db.manualConvert(driveURL), nil
	}
}

func (db *DriveBypasser) manualConvert(url string) string {
	// Convierte https://drive.google.com/file/d/ID/view -> https://drive.google.com/uc?export=download&id=ID
	parts := strings.Split(url, "/")
	var id string
	for i, p := range parts {
		if p == "d" && i+1 < len(parts) {
			id = parts[i+1]
			break
		}
	}
	if id == "" {
		return url
	}
	return fmt.Sprintf("https://drive.google.com/uc?export=download&id=%s", id)
}
