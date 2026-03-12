package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var artifactsCmd = &cobra.Command{
	Use:   "artifacts",
	Short: "Manage artifacts",
}

var artifactsListTrailID string

var artifactsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List artifacts for a trail",
	RunE: func(cmd *cobra.Command, args []string) error {
		if artifactsListTrailID == "" {
			return fmt.Errorf("--trail-id is required")
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		artifacts, err := api.ListArtifacts(c, artifactsListTrailID)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(artifacts)
			return nil
		}
		rows := make([][]string, len(artifacts))
		for i, a := range artifacts {
			rows[i] = []string{a.ID, a.ImageName, a.ImageTag, a.Registry, truncate(a.Sha256Digest, 16), a.ReportedBy, a.ReportedAt}
		}
		output.PrintTable([]string{"ID", "IMAGE", "TAG", "REGISTRY", "SHA256", "REPORTED BY", "REPORTED AT"}, rows)
		return nil
	},
}

var artifactsFindSha256 string

var artifactsFindCmd = &cobra.Command{
	Use:   "find",
	Short: "Find an artifact by SHA-256 digest",
	RunE: func(cmd *cobra.Command, args []string) error {
		if artifactsFindSha256 == "" {
			return fmt.Errorf("--sha256 is required")
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		artifact, err := api.FindArtifact(c, artifactsFindSha256)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(artifact)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"ID", artifact.ID},
				{"Trail ID", artifact.TrailID},
				{"Image", artifact.ImageName},
				{"Tag", artifact.ImageTag},
				{"Registry", artifact.Registry},
				{"SHA256", artifact.Sha256Digest},
				{"Reported By", artifact.ReportedBy},
				{"Reported At", artifact.ReportedAt},
			},
		)
		return nil
	},
}

var (
	artifactCreateTrailID    string
	artifactCreateImageName  string
	artifactCreateImageTag   string
	artifactCreateSha256     string
	artifactCreateReportedBy string
	artifactCreateRegistry   string
)

var artifactsCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Register a new artifact on a trail",
	RunE: func(cmd *cobra.Command, args []string) error {
		for flag, val := range map[string]string{
			"--trail-id":    artifactCreateTrailID,
			"--image-name":  artifactCreateImageName,
			"--image-tag":   artifactCreateImageTag,
			"--sha256":      artifactCreateSha256,
			"--reported-by": artifactCreateReportedBy,
		} {
			if val == "" {
				return fmt.Errorf("%s is required", flag)
			}
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		artifact, err := api.CreateArtifact(c, artifactCreateTrailID, api.CreateArtifactRequest{
			ImageName:    artifactCreateImageName,
			ImageTag:     artifactCreateImageTag,
			Sha256Digest: artifactCreateSha256,
			Registry:     artifactCreateRegistry,
			ReportedBy:   artifactCreateReportedBy,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(artifact)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Artifact created: %s", artifact.ID))
		return nil
	},
}

func init() {
	artifactsListCmd.Flags().StringVar(&artifactsListTrailID, "trail-id", "", "Trail ID (required)")
	artifactsFindCmd.Flags().StringVar(&artifactsFindSha256, "sha256", "", "SHA-256 digest (required)")

	artifactsCreateCmd.Flags().StringVar(&artifactCreateTrailID, "trail-id", "", "Trail ID (required)")
	artifactsCreateCmd.Flags().StringVar(&artifactCreateImageName, "image-name", "", "Image name (required)")
	artifactsCreateCmd.Flags().StringVar(&artifactCreateImageTag, "image-tag", "", "Image tag (required)")
	artifactsCreateCmd.Flags().StringVar(&artifactCreateSha256, "sha256", "", "SHA-256 digest (required)")
	artifactsCreateCmd.Flags().StringVar(&artifactCreateReportedBy, "reported-by", "", "Reporter name (required)")
	artifactsCreateCmd.Flags().StringVar(&artifactCreateRegistry, "registry", "", "Container registry")

	artifactsCmd.AddCommand(artifactsListCmd, artifactsFindCmd, artifactsCreateCmd)
}
