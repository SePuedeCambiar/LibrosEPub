package providers

import (
	"context"
	"fmt"
	"io"
        "crypto/tls"
	"net/http" // <--- ESTO ES LO QUE FALTABA
	"reader-backend/internal/epub"
	"reader-backend/internal/models"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
)

// LightNovelProvider implementa BookProvider para fuentes de novelas ligeras en español
type LightNovelProvider struct {
	client  *http.Client
	baseURL string
}


func NewLightNovelProvider(client *http.Client) *LightNovelProvider {
	// Creamos un cliente HTTP que ignora errores de certificados SSL (InsecureSkipVerify)
	// Esto es necesario para sitios de novelas que tienen certificados mal configurados
	customTransport := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	
	httpClient := &http.Client{
		Timeout:   30 * time.Second,
		Transport: customTransport,
	}

	return &LightNovelProvider{
		client:  httpClient,
		baseURL: "https://novelasligera.com",
	}
}


func (p *LightNovelProvider) Name() string {
	return "ln_spanish"
}

// Search busca novelas ligeras por palabra clave
func (p *LightNovelProvider) Search(ctx context.Context, filter models.SearchFilter) ([]models.Book, error) {
	searchURL := fmt.Sprintf("%s/?s=%s&post_type=wp-manga", p.baseURL, strings.ReplaceAll(filter.Query, " ", "+"))

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ReaderEcosystem/1.0")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return nil, err
	}

	var books []models.Book
	doc.Find(".c-tabs-item__content, .row.c-tabs-item__content").Each(func(i int, s *goquery.Selection) {
		if filter.Limit > 0 && len(books) >= filter.Limit {
			return
		}

		titleTag := s.Find(".post-title h3 a, .post-title h4 a").First()
		title := strings.TrimSpace(titleTag.Text())
		novelURL, exists := titleTag.Attr("href")
		if !exists || title == "" {
			return
		}

		coverURL, _ := s.Find(".tab-thumb img").Attr("src")
		author := strings.TrimSpace(s.Find(".mg_author .summary-content a").Text())
		if author == "" {
			author = "Traducción al Español"
		}

		books = append(books, models.Book{
			ID:          novelURL,
			Title:       title,
			Authors:     []string{author},
			Language:    "es",
			CoverURL:    coverURL,
			DownloadURL: novelURL,
			Extension:   "epub",
			Provider:    p.Name(),
		})
	})

	return books, nil
}

// ScrapeAndBuildEpub descarga los capítulos de una novela y la compila en un archivo EPUB en disco
func (p *LightNovelProvider) ScrapeAndBuildEpub(ctx context.Context, novelURL, destPath string, maxChapters int) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, novelURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ReaderEcosystem/1.0")

	resp, err := p.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return err
	}

	title := strings.TrimSpace(doc.Find(".post-title h1").Text())
	if title == "" {
		title = "Novela Ligera"
	}
	author := strings.TrimSpace(doc.Find(".author-content a").Text())
	coverURL, _ := doc.Find(".summary_image img").Attr("src")

	var coverData []byte
	if coverURL != "" {
		if cResp, err := p.client.Get(coverURL); err == nil && cResp.StatusCode == http.StatusOK {
			coverData, _ = io.ReadAll(cResp.Body)
			cResp.Body.Close()
		}
	}

	type chapterLink struct {
		title string
		url   string
	}
	var links []chapterLink

	doc.Find("li.wp-manga-chapter a").Each(func(i int, s *goquery.Selection) {
		href, exists := s.Attr("href")
		if exists {
			chTitle := strings.TrimSpace(s.Text())
			links = append(links, chapterLink{title: chTitle, url: href})
		}
	})

	for i, j := 0, len(links)-1; i < j; i, j = i+1, j-1 {
		links[i], links[j] = links[j], links[i]
	}

	if maxChapters > 0 && len(links) > maxChapters {
		links = links[:maxChapters]
	}

	var chapters []epub.Chapter
	for idx, link := range links {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		chReq, _ := http.NewRequestWithContext(ctx, http.MethodGet, link.url, nil)
		chReq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ReaderEcosystem/1.0")
		chResp, err := p.client.Do(chReq)
		if err != nil {
			continue
		}

		chDoc, err := goquery.NewDocumentFromReader(chResp.Body)
		chResp.Body.Close()
		if err != nil {
			continue
		}

		var textContent strings.Builder
		chDoc.Find(".reading-content p, .text-left p, .entry-content p").Each(func(_ int, ps *goquery.Selection) {
			pText := strings.TrimSpace(ps.Text())
			if pText != "" {
				textContent.WriteString(fmt.Sprintf("<p>%s</p>\n", pText))
			}
		})

		chapters = append(chapters, epub.Chapter{
			Title:   fmt.Sprintf("Capítulo %d: %s", idx+1, link.title),
			Content: textContent.String(),
		})

		time.Sleep(100 * time.Millisecond)
	}

	bookData := epub.BookData{
		Title:     title,
		Author:    author,
		Language:  "es",
		CoverData: coverData,
		Chapters:  chapters,
	}

	return epub.BuildEpub(destPath, bookData)
}
