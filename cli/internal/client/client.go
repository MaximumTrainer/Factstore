package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	maxRetries = 3
	baseDelay  = time.Second
)

// Client is an HTTP client for the Factstore API.
type Client struct {
	BaseURL    string
	Token      string
	httpClient *http.Client
}

// New creates a new Client.
func New(baseURL, token string) *Client {
	if strings.HasPrefix(baseURL, "http://") {
		fmt.Fprintln(os.Stderr, "warning: using http:// — consider using https:// for production")
	}
	return &Client{
		BaseURL: strings.TrimRight(baseURL, "/"),
		Token:   token,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (c *Client) newRequest(method, path string, body interface{}) (*http.Request, error) {
	var bodyReader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("marshal request body: %w", err)
		}
		bodyReader = bytes.NewBuffer(data)
	}

	url := c.BaseURL + path
	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set("Accept", "application/json")
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}
	return req, nil
}

func (c *Client) do(req *http.Request) ([]byte, int, error) {
	var (
		resp *http.Response
		err  error
	)
	delay := baseDelay
	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			time.Sleep(delay)
			delay *= 2
		}
		resp, err = c.httpClient.Do(req)
		if err == nil {
			break
		}
	}
	if err != nil {
		return nil, 0, fmt.Errorf("request failed after %d retries: %w", maxRetries, err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("read response body: %w", err)
	}
	return data, resp.StatusCode, nil
}

func (c *Client) doRequest(method, path string, body interface{}) ([]byte, int, error) {
	req, err := c.newRequest(method, path, body)
	if err != nil {
		return nil, 0, err
	}
	return c.do(req)
}

// Get performs a GET request.
func (c *Client) Get(path string) ([]byte, int, error) {
	return c.doRequest(http.MethodGet, path, nil)
}

// Post performs a POST request with a JSON body.
func (c *Client) Post(path string, body interface{}) ([]byte, int, error) {
	return c.doRequest(http.MethodPost, path, body)
}

// Put performs a PUT request with a JSON body.
func (c *Client) Put(path string, body interface{}) ([]byte, int, error) {
	return c.doRequest(http.MethodPut, path, body)
}

// Delete performs a DELETE request.
func (c *Client) Delete(path string) ([]byte, int, error) {
	return c.doRequest(http.MethodDelete, path, nil)
}

// PostMultipart uploads a file as multipart/form-data.
func (c *Client) PostMultipart(path, fieldName, filePath string) ([]byte, int, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, 0, fmt.Errorf("open file %s: %w", filePath, err)
	}
	defer f.Close()

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, err := writer.CreateFormFile(fieldName, filePath)
	if err != nil {
		return nil, 0, fmt.Errorf("create form file: %w", err)
	}
	if _, err = io.Copy(part, f); err != nil {
		return nil, 0, fmt.Errorf("copy file content: %w", err)
	}
	if err = writer.Close(); err != nil {
		return nil, 0, fmt.Errorf("close multipart writer: %w", err)
	}

	url := c.BaseURL + path
	req, err := http.NewRequest(http.MethodPost, url, &buf)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Accept", "application/json")
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}
	return c.do(req)
}

// ParseError extracts a user-friendly error from an API response body.
func ParseError(statusCode int, body []byte) error {
	var apiErr struct {
		Message string `json:"message"`
		Error   string `json:"error"`
	}
	if json.Unmarshal(body, &apiErr) == nil && apiErr.Message != "" {
		return fmt.Errorf("API error %d: %s", statusCode, apiErr.Message)
	}
	if json.Unmarshal(body, &apiErr) == nil && apiErr.Error != "" {
		return fmt.Errorf("API error %d: %s", statusCode, apiErr.Error)
	}
	if len(body) > 0 {
		return fmt.Errorf("API error %d: %s", statusCode, string(body))
	}
	return fmt.Errorf("API error %d", statusCode)
}
