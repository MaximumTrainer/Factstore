package commands

import (
	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var reportCmd = &cobra.Command{
	Use:   "report",
	Short: "Generate reports",
}

var reportDeploymentsOrgSlug string

var reportDeploymentsCmd = &cobra.Command{
	Use:   "deployments",
	Short: "Show all current deployments across all environments",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		artifacts, err := api.GetLiveArtifacts(c)
		if err != nil {
			return err
		}
		if reportDeploymentsOrgSlug != "" {
			filtered := artifacts[:0]
			for _, a := range artifacts {
				if a.OrgSlug == reportDeploymentsOrgSlug {
					filtered = append(filtered, a)
				}
			}
			artifacts = filtered
		}
		if jsonOutput {
			output.PrintJSON(artifacts)
			return nil
		}
		rows := make([][]string, len(artifacts))
		for i, a := range artifacts {
			rows[i] = []string{a.ArtifactName, a.EnvironmentName, a.ArtifactTag, truncate(a.ArtifactSha256, 16)}
		}
		output.PrintTable([]string{"IMAGE", "ENVIRONMENT", "TAG", "SHA256"}, rows)
		return nil
	},
}

func init() {
	reportDeploymentsCmd.Flags().StringVar(&reportDeploymentsOrgSlug, "org-slug", "", "Filter by organisation slug")

	reportCmd.AddCommand(reportDeploymentsCmd)
}
