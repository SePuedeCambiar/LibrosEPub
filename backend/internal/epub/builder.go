package epub

import (
	"archive/zip"
	"fmt"
	"html"
	"io"
	"os"
	"strings"
	"time"
)

type Chapter struct {
	Title   string
	Content string
}

type BookData struct {
	Title       string
	Author      string
	Language    string
	CoverURL    string
	CoverData   []byte
	Chapters    []Chapter
	Identifier  string
	PublishedAt time.Time
}

func BuildEpub(destPath string, data BookData) error {
	if data.Language == "" {
		data.Language = "es"
	}
	if data.Author == "" {
		data.Author = "Novela Ligera"
	}
	if data.Identifier == "" {
		data.Identifier = fmt.Sprintf("urn:uuid:reader-%d", time.Now().UnixNano())
	}

	file, err := os.Create(destPath)
	if err != nil {
		return fmt.Errorf("error creando archivo EPUB en disco: %w", err)
	}
	defer file.Close()

	zipWriter := zip.NewWriter(file)
	defer zipWriter.Close()

	// 1. mimetype
	mimetypeHeader := &zip.FileHeader{
		Name:   "mimetype",
		Method: zip.Store,
	}
	mimetypeWriter, err := zipWriter.CreateHeader(mimetypeHeader)
	if err != nil {
		return err
	}
	if _, err := mimetypeWriter.Write([]byte("application/epub+zip")); err != nil {
		return err
	}

	// 2. container.xml
	if err := writeZipFile(zipWriter, "META-INF/container.xml", `<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
   <rootfiles>
      <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
   </rootfiles>
</container>`); err != nil {
		return err
	}

	// 3. CSS
	cssContent := `body { font-family: sans-serif; margin: 5% 5%; text-align: justify; line-height: 1.6; }
h1, h2 { text-align: center; color: #333; margin-top: 1em; margin-bottom: 0.5em; }
p { text-indent: 1.2em; margin-top: 0; margin-bottom: 0.8em; }
img { max-width: 100%; height: auto; display: block; margin: 1em auto; }`
	if err := writeZipFile(zipWriter, "OEBPS/styles.css", cssContent); err != nil {
		return err
	}

	// 4. Portada
	if len(data.CoverData) > 0 {
		coverWriter, err := zipWriter.Create("OEBPS/cover.jpg")
		if err != nil {
			return err
		}
		if _, err := coverWriter.Write(data.CoverData); err != nil {
			return err
		}
	}

	// 5. Capítulos
	for i, ch := range data.Chapters {
		filename := fmt.Sprintf("OEBPS/chapter_%d.xhtml", i+1)
		cleanHTML := sanitizeHTMLForEpub(ch.Content)
		xhtml := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <title>%s</title>
    <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
    <h2>%s</h2>
    %s
</body>
</html>`, html.EscapeString(ch.Title), html.EscapeString(ch.Title), cleanHTML)

		if err := writeZipFile(zipWriter, filename, xhtml); err != nil {
			return err
		}
	}

	// 6. toc.ncx
	var ncxNavPoints strings.Builder
	for i, ch := range data.Chapters {
		ncxNavPoints.WriteString(fmt.Sprintf(`
    <navPoint id="navpoint-%d" playOrder="%d">
      <navLabel><text>%s</text></navLabel>
      <content src="chapter_%d.xhtml"/>
    </navPoint>`, i+1, i+1, html.EscapeString(ch.Title), i+1))
	}

	ncxContent := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="%s"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>%s</text></docTitle>
  <navMap>%s
  </navMap>
</ncx>`, data.Identifier, html.EscapeString(data.Title), ncxNavPoints.String())

	if err := writeZipFile(zipWriter, "OEBPS/toc.ncx", ncxContent); err != nil {
		return err
	}

	// 7. content.opf
	var manifestItems strings.Builder
	var spineItems strings.Builder

	if len(data.CoverData) > 0 {
		manifestItems.WriteString(`    <item id="cover-image" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>` + "\n")
	}
	manifestItems.WriteString(`    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>` + "\n")
	manifestItems.WriteString(`    <item id="css" href="styles.css" media-type="text/css"/>` + "\n")

	for i := range data.Chapters {
		id := fmt.Sprintf("chap_%d", i+1)
		manifestItems.WriteString(fmt.Sprintf(`    <item id="%s" href="chapter_%d.xhtml" media-type="application/xhtml+xml"/>`+"\n", id, i+1))
		spineItems.WriteString(fmt.Sprintf(`    <itemref idref="%s"/>`+"\n", id))
	}

	opfContent := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
    <dc:identifier id="BookId">%s</dc:identifier>
    <dc:title>%s</dc:title>
    <dc:creator opf:role="aut">%s</dc:creator>
    <dc:language>%s</dc:language>
    <meta name="cover" content="cover-image"/>
  </metadata>
  <manifest>
%s  </manifest>
  <spine toc="ncx">
%s  </spine>
</package>`, data.Identifier, html.EscapeString(data.Title), html.EscapeString(data.Author), data.Language, manifestItems.String(), spineItems.String())

	return writeZipFile(zipWriter, "OEBPS/content.opf", opfContent)
}

func writeZipFile(zw *zip.Writer, name, content string) error {
	w, err := zw.Create(name)
	if err != nil {
		return err
	}
	_, err = io.WriteString(w, content)
	return err
}

func sanitizeHTMLForEpub(raw string) string {
	if !strings.Contains(raw, "<p>") {
		lines := strings.Split(raw, "\n")
		var sb strings.Builder
		for _, line := range lines {
			t := strings.TrimSpace(line)
			if t != "" {
				sb.WriteString(fmt.Sprintf("<p>%s</p>\n", html.EscapeString(t)))
			}
		}
		return sb.String()
	}
	return raw
}
