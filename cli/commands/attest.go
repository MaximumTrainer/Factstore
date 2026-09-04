package commands

import (
	"fmt"
	"os"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"
	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var attestCmd = &cobra.Command{
	Use:   "attest",
	Short: "Record typed attestations",
}

// Shared across every attest subcommand: a downstream pipeline that was handed only the
// release identifier can address the trail without plumbing a UUID (#164).
var (
	attestFlowID          string
	attestTrailExternalID string
	attestTrailName       string
)

func resolveAttestTrailID(c *client.Client, trailID string) (string, error) {
	return api.ResolveTrailID(c, trailID, attestFlowID, api.TrailSelector{
		ExternalID: attestTrailExternalID,
		Name:       attestTrailName,
	})
}

var (
	attestJunitTrailID         string
	attestJunitName            string
	attestJunitTestResultsFile string
	attestJunitGitCommitSha    string
	attestJunitGitBranch       string
	attestJunitDetails         string
)

var attestJunitCmd = &cobra.Command{
	Use:   "junit",
	Short: "Record a JUnit test attestation",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		req := api.TypedAttestRequest{
			Type:         "junit",
			Status:       "PASSED",
			Name:         attestJunitName,
			Details:      attestJunitDetails,
			GitCommitSha: attestJunitGitCommitSha,
			GitBranch:    attestJunitGitBranch,
		}
		if attestJunitTestResultsFile != "" {
			data, readErr := os.ReadFile(attestJunitTestResultsFile)
			if readErr != nil {
				return fmt.Errorf("read test results file: %w", readErr)
			}
			req.AttestationData = string(data)
		}
		trailID, err := resolveAttestTrailID(c, attestJunitTrailID)
		if err != nil {
			return err
		}
		result, err := api.RecordTypedAttestation(c, trailID, req)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Attestation created: %s", result.ID))
		return nil
	},
}

var (
	attestSnykTrailID      string
	attestSnykName         string
	attestSnykScanFile     string
	attestSnykGitCommitSha string
	attestSnykGitBranch    string
	attestSnykStatus       string
)

var attestSnykCmd = &cobra.Command{
	Use:   "snyk",
	Short: "Record a Snyk security scan attestation",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		req := api.TypedAttestRequest{
			Type:         "snyk",
			Status:       attestSnykStatus,
			Name:         attestSnykName,
			GitCommitSha: attestSnykGitCommitSha,
			GitBranch:    attestSnykGitBranch,
		}
		if attestSnykScanFile != "" {
			data, readErr := os.ReadFile(attestSnykScanFile)
			if readErr != nil {
				return fmt.Errorf("read scan results file: %w", readErr)
			}
			req.AttestationData = string(data)
		}
		trailID, err := resolveAttestTrailID(c, attestSnykTrailID)
		if err != nil {
			return err
		}
		result, err := api.RecordTypedAttestation(c, trailID, req)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Attestation created: %s", result.ID))
		return nil
	},
}

var (
	attestGenericTrailID      string
	attestGenericType         string
	attestGenericStatus       string
	attestGenericName         string
	attestGenericDetails      string
	attestGenericEvidenceURL  string
	attestGenericGitCommitSha string
	attestGenericGitBranch    string
)

var attestGenericCmd = &cobra.Command{
	Use:   "generic",
	Short: "Record a generic attestation",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trailID, err := resolveAttestTrailID(c, attestGenericTrailID)
		if err != nil {
			return err
		}
		result, err := api.RecordTypedAttestation(c, trailID, api.TypedAttestRequest{
			Type:         attestGenericType,
			Status:       attestGenericStatus,
			Name:         attestGenericName,
			Details:      attestGenericDetails,
			EvidenceUrl:  attestGenericEvidenceURL,
			GitCommitSha: attestGenericGitCommitSha,
			GitBranch:    attestGenericGitBranch,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Attestation created: %s", result.ID))
		return nil
	},
}

func init() {
	attestJunitCmd.Flags().StringVar(&attestJunitTrailID, "trail-id", "", "Trail ID (or resolve one with --flow-id and --trail-external-id/--trail-name)")
	attestJunitCmd.Flags().StringVar(&attestJunitName, "name", "junit", "Attestation name")
	attestJunitCmd.Flags().StringVar(&attestJunitTestResultsFile, "test-results-file", "", "Path to JUnit XML results file")
	attestJunitCmd.Flags().StringVar(&attestJunitGitCommitSha, "git-commit-sha", "", "Git commit SHA")
	attestJunitCmd.Flags().StringVar(&attestJunitGitBranch, "git-branch", "", "Git branch name")
	attestJunitCmd.Flags().StringVar(&attestJunitDetails, "details", "", "Additional details")

	attestSnykCmd.Flags().StringVar(&attestSnykTrailID, "trail-id", "", "Trail ID (or resolve one with --flow-id and --trail-external-id/--trail-name)")
	attestSnykCmd.Flags().StringVar(&attestSnykName, "name", "snyk", "Attestation name")
	attestSnykCmd.Flags().StringVar(&attestSnykScanFile, "scan-results-file", "", "Path to Snyk scan results file")
	attestSnykCmd.Flags().StringVar(&attestSnykGitCommitSha, "git-commit-sha", "", "Git commit SHA")
	attestSnykCmd.Flags().StringVar(&attestSnykGitBranch, "git-branch", "", "Git branch name")
	attestSnykCmd.Flags().StringVar(&attestSnykStatus, "status", "PASSED", "Attestation status (PASSED/FAILED/PENDING)")

	attestGenericCmd.Flags().StringVar(&attestGenericTrailID, "trail-id", "", "Trail ID (or resolve one with --flow-id and --trail-external-id/--trail-name)")
	attestGenericCmd.Flags().StringVar(&attestGenericType, "type", "", "Attestation type (required)")
	attestGenericCmd.Flags().StringVar(&attestGenericStatus, "status", "", "Attestation status: PASSED, FAILED, or PENDING (required)")
	attestGenericCmd.Flags().StringVar(&attestGenericName, "name", "", "Attestation name")
	attestGenericCmd.Flags().StringVar(&attestGenericDetails, "details", "", "Additional details")
	attestGenericCmd.Flags().StringVar(&attestGenericEvidenceURL, "evidence-url", "", "URL to external evidence")
	attestGenericCmd.Flags().StringVar(&attestGenericGitCommitSha, "git-commit-sha", "", "Git commit SHA")
	attestGenericCmd.Flags().StringVar(&attestGenericGitBranch, "git-branch", "", "Git branch name")
	_ = attestGenericCmd.MarkFlagRequired("type")
	_ = attestGenericCmd.MarkFlagRequired("status")

	attestCmd.PersistentFlags().StringVar(&attestFlowID, "flow-id", "",
		"Flow the trail belongs to; required when resolving by --trail-external-id or --trail-name")
	attestCmd.PersistentFlags().StringVar(&attestTrailExternalID, "trail-external-id", "",
		"Release identifier of the trail to attest against, instead of --trail-id")
	attestCmd.PersistentFlags().StringVar(&attestTrailName, "trail-name", "",
		"Name of the trail to attest against, instead of --trail-id")

	attestCmd.AddCommand(attestJunitCmd, attestSnykCmd, attestGenericCmd)
}
