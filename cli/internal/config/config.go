package config

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/viper"
)

const (
	defaultConfigFile = ".factstore.yaml"
	KeyHost           = "host"
	KeyToken          = "token"
)

// Config holds CLI configuration values.
type Config struct {
	Host  string
	Token string
}

// Load initializes Viper and returns the current configuration.
func Load() (*Config, error) {
	viper.SetConfigName(".factstore")
	viper.SetConfigType("yaml")
	viper.AddConfigPath("$HOME")

	home, err := os.UserHomeDir()
	if err != nil {
		return nil, fmt.Errorf("cannot determine home directory: %w", err)
	}
	viper.AddConfigPath(home)

	viper.SetEnvPrefix("FACTSTORE")
	viper.AutomaticEnv()

	// Non-fatal — config file may not exist yet.
	_ = viper.ReadInConfig()

	return &Config{
		Host:  viper.GetString(KeyHost),
		Token: viper.GetString(KeyToken),
	}, nil
}

// Save writes host and token to ~/.factstore.yaml.
func Save(host, token string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return fmt.Errorf("cannot determine home directory: %w", err)
	}
	path := filepath.Join(home, defaultConfigFile)

	viper.Set(KeyHost, host)
	viper.Set(KeyToken, token)
	viper.SetConfigFile(path)

	return viper.WriteConfigAs(path)
}
