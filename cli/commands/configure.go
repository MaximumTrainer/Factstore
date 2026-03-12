package commands

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/config"
	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/spf13/cobra"
)

var configureCmd = &cobra.Command{
	Use:   "configure",
	Short: "Set the API host and authentication token",
	Long:  "Interactively set the Factstore API host and bearer token, saved to ~/.factstore.yaml.",
	RunE: func(cmd *cobra.Command, args []string) error {
		reader := bufio.NewReader(os.Stdin)

		fmt.Print("API host (e.g. https://api.factstore.example.com): ")
		host, err := reader.ReadString('\n')
		if err != nil {
			return fmt.Errorf("read host: %w", err)
		}
		host = strings.TrimSpace(host)

		fmt.Print("Bearer token: ")
		token, err := reader.ReadString('\n')
		if err != nil {
			return fmt.Errorf("read token: %w", err)
		}
		token = strings.TrimSpace(token)

		if err := config.Save(host, token); err != nil {
			return fmt.Errorf("save config: %w", err)
		}
		output.PrintSuccess("Configuration saved to ~/.factstore.yaml")
		return nil
	},
}
