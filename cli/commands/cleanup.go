package commands

import (
	"fmt"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

// Retiring obsolete trails (#161). Archiving is the default and reversible; deletion is the
// deliberate exception and reports everything it removed.

var trailsArchiveCmd = &cobra.Command{
	Use:   "archive <id>",
	Short: "Archive a trail (soft delete, reversible)",
	Long: `Archive a trail.

The trail drops out of the default listings but every piece of evidence recorded against it
is retained. This is the right way to retire a trail: it is reversible with 'unarchive'.`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.ArchiveTrail(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Trail %s archived", trail.ID))
		return nil
	},
}

var trailsUnarchiveCmd = &cobra.Command{
	Use:   "unarchive <id>",
	Short: "Bring an archived trail back",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.UnarchiveTrail(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Trail %s unarchived", trail.ID))
		return nil
	},
}

var trailDeleteYes bool

var trailsDeleteCmd = &cobra.Command{
	Use:   "delete <id>",
	Short: "Permanently delete a trail and the evidence it owns",
	Long: `Permanently delete a trail.

Cascades to attestations, artifacts, evidence files, approvals, coverage reports, security
scans, compliance assessments and Jira tickets. The audit log and the append-only ledger are
left intact, so the record that the evidence existed survives its removal.

Prefer 'factstore trails archive' unless this is throwaway test data.`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		if !trailDeleteYes {
			return fmt.Errorf("deleting a trail destroys its evidence; pass --yes to confirm, or use 'trails archive'")
		}
		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.DeleteTrail(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Trail %s deleted", args[0]))
		output.PrintTable([]string{"REMOVED", "COUNT"}, cascadeRows(result.Cascade))
		return nil
	},
}

var (
	cleanupFlowID    string
	cleanupTag       string
	cleanupOlderThan string
	cleanupMode      string
	cleanupApply     bool
)

var trailsCleanupCmd = &cobra.Command{
	Use:   "cleanup",
	Short: "Bulk cleanup of trails by flow, tag or age",
	Long: `Bulk cleanup of trails, for tearing down evaluation and demo data.

Reports what would happen and changes nothing unless --apply is given. At least one selector
is required, so a mistyped command cannot select every trail in the system.

  factstore trails cleanup --flow-id "$FLOW_ID"
  factstore trails cleanup --tag env=demo --mode DELETE --apply
  factstore trails cleanup --older-than 2026-01-01T00:00:00Z --apply`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if cleanupFlowID == "" && cleanupTag == "" && cleanupOlderThan == "" {
			return fmt.Errorf("one of --flow-id, --tag or --older-than is required")
		}
		mode := strings.ToUpper(cleanupMode)
		if mode != "ARCHIVE" && mode != "DELETE" {
			return fmt.Errorf("--mode must be ARCHIVE or DELETE, got %q", cleanupMode)
		}
		req := api.TrailCleanupRequest{
			FlowID:    cleanupFlowID,
			OlderThan: cleanupOlderThan,
			Mode:      mode,
			DryRun:    !cleanupApply,
		}
		if cleanupTag != "" {
			key, value, found := strings.Cut(cleanupTag, "=")
			if !found || key == "" {
				return fmt.Errorf("--tag must be key=value, got %q", cleanupTag)
			}
			req.TagKey = key
			req.TagValue = value
		}

		c, err := newClient()
		if err != nil {
			return err
		}
		result, err := api.CleanupTrails(c, req)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		verb := "would be"
		if !result.DryRun {
			verb = "were"
		}
		output.PrintSuccess(fmt.Sprintf("%d trail(s) %s %sd", result.TrailCount, verb, strings.ToLower(result.Mode)))
		output.PrintTable([]string{"EVIDENCE", "COUNT"}, cascadeRows(result.Cascade))
		if result.DryRun {
			output.PrintSuccess("Dry run - nothing was changed. Re-run with --apply to proceed.")
		}
		return nil
	},
}

func cascadeRows(c api.TrailCascadeCounts) [][]string {
	return [][]string{
		{"Attestations", fmt.Sprint(c.Attestations)},
		{"Artifacts", fmt.Sprint(c.Artifacts)},
		{"Evidence files", fmt.Sprint(c.EvidenceFiles)},
		{"Approvals", fmt.Sprint(c.Approvals)},
		{"Coverage reports", fmt.Sprint(c.CoverageReports)},
		{"Security scans", fmt.Sprint(c.SecurityScans)},
		{"Compliance assessments", fmt.Sprint(c.ComplianceAssessments)},
		{"Jira tickets", fmt.Sprint(c.JiraTickets)},
		{"Total", fmt.Sprint(c.Total)},
	}
}

func init() {
	trailsDeleteCmd.Flags().BoolVar(&trailDeleteYes, "yes", false, "Confirm permanent deletion of the evidence")

	trailsCleanupCmd.Flags().StringVar(&cleanupFlowID, "flow-id", "", "Select trails in this flow")
	trailsCleanupCmd.Flags().StringVar(&cleanupTag, "tag", "", "Select trails carrying this key=value tag")
	trailsCleanupCmd.Flags().StringVar(&cleanupOlderThan, "older-than", "",
		"Select trails created before this RFC 3339 instant (e.g. 2026-01-01T00:00:00Z)")
	trailsCleanupCmd.Flags().StringVar(&cleanupMode, "mode", "ARCHIVE", "ARCHIVE (reversible) or DELETE")
	trailsCleanupCmd.Flags().BoolVar(&cleanupApply, "apply", false, "Actually apply the cleanup instead of reporting it")

	trailsCmd.AddCommand(trailsArchiveCmd, trailsUnarchiveCmd, trailsDeleteCmd, trailsCleanupCmd)
}
