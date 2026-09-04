package commands

import (
	"fmt"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var trailsCmd = &cobra.Command{
	Use:   "trails",
	Short: "Manage trails",
}

var trailsListFlowID string

var trailsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all trails",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trails, err := api.ListTrails(c, trailsListFlowID)
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trails)
			return nil
		}
		rows := make([][]string, len(trails))
		for i, t := range trails {
			rows[i] = []string{t.ID, t.FlowID, t.GitBranch, truncate(t.GitCommitSha, 8), t.GitAuthor, t.Status, t.CreatedAt}
		}
		output.PrintTable([]string{"ID", "FLOW ID", "BRANCH", "COMMIT", "AUTHOR", "STATUS", "CREATED AT"}, rows)
		return nil
	},
}

var trailsGetCmd = &cobra.Command{
	Use:   "get <id>",
	Short: "Get a trail by ID",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.GetTrail(c, args[0])
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"ID", trail.ID},
				{"Flow ID", trail.FlowID},
				{"Commit SHA", trail.GitCommitSha},
				{"Branch", trail.GitBranch},
				{"Author", trail.GitAuthor},
				{"Author Email", trail.GitAuthorEmail},
				{"PR ID", trail.PullRequestID},
				{"PR Reviewer", trail.PullRequestReviewer},
				{"Deployment Actor", trail.DeploymentActor},
				{"Status", trail.Status},
				{"Created At", trail.CreatedAt},
				{"Updated At", trail.UpdatedAt},
			},
		)
		return nil
	},
}

var (
	trailLookupFlowID     string
	trailLookupExternalID string
	trailLookupName       string
	trailLookupCommit     string
)

var trailsLookupCmd = &cobra.Command{
	Use:   "lookup",
	Short: "Resolve a trail without knowing its UUID",
	Long: `Resolve a trail within a flow by release identifier, name, or commit SHA.

Lets a downstream pipeline - integration tests, API tests, environment testing - attach its
attestations to the trail the primary pipeline created, using the release identifier it was
handed rather than a UUID.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.LookupTrail(c, trailLookupFlowID, api.TrailSelector{
			ExternalID:   trailLookupExternalID,
			Name:         trailLookupName,
			GitCommitSha: trailLookupCommit,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			[][]string{
				{"ID", trail.ID},
				{"Flow ID", trail.FlowID},
				{"External ID", trail.ExternalID},
				{"Name", trail.Name},
				{"Commit SHA", trail.GitCommitSha},
				{"Branch", trail.GitBranch},
				{"Status", trail.Status},
				{"Created At", trail.CreatedAt},
			},
		)
		return nil
	},
}

var (
	trailCreateFlowID      string
	trailCreateCommit      string
	trailCreateBranch      string
	trailCreateAuthor      string
	trailCreateAuthorEmail string
	trailCreatePRID        string
	trailCreatePRReviewer  string
	trailCreateDeployActor string
	trailCreateName        string
	trailCreateExternalID  string
)

var trailsCreateCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new trail",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		trail, err := api.CreateTrail(c, api.CreateTrailRequest{
			FlowID:              trailCreateFlowID,
			GitCommitSha:        trailCreateCommit,
			GitBranch:           trailCreateBranch,
			GitAuthor:           trailCreateAuthor,
			GitAuthorEmail:      trailCreateAuthorEmail,
			PullRequestID:       trailCreatePRID,
			PullRequestReviewer: trailCreatePRReviewer,
			DeploymentActor:     trailCreateDeployActor,
			Name:                trailCreateName,
			ExternalID:          trailCreateExternalID,
		})
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(trail)
			return nil
		}
		// With --external-id, creation is a get-or-create: a re-run joins the existing
		// trail for that release rather than forking the evidence.
		if trail.Status == "exists" {
			output.PrintSuccess(fmt.Sprintf("Trail already exists for this release: %s", trail.ID))
			return nil
		}
		output.PrintSuccess(fmt.Sprintf("Trail created: %s", trail.ID))
		return nil
	},
}

// truncate returns s[:n] if len(s) >= n, otherwise s. Safe for empty strings.
func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

func init() {
	trailsListCmd.Flags().StringVar(&trailsListFlowID, "flow-id", "", "Filter by flow ID")

	trailsCreateCmd.Flags().StringVar(&trailCreateFlowID, "flow-id", "", "Flow ID (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateCommit, "commit", "", "Git commit SHA (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateBranch, "branch", "", "Git branch (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateAuthor, "author", "", "Git author name (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreateAuthorEmail, "author-email", "", "Git author email (required)")
	trailsCreateCmd.Flags().StringVar(&trailCreatePRID, "pr-id", "", "Pull request ID")
	trailsCreateCmd.Flags().StringVar(&trailCreatePRReviewer, "pr-reviewer", "", "Pull request reviewer")
	trailsCreateCmd.Flags().StringVar(&trailCreateDeployActor, "deployment-actor", "", "Deployment actor")
	trailsCreateCmd.Flags().StringVar(&trailCreateName, "name", "", "Human-readable trail name")
	trailsCreateCmd.Flags().StringVar(&trailCreateExternalID, "external-id", "",
		"Stable release identifier (build number, run id, release tag). Creation is idempotent per flow, "+
			"so a re-run joins the same trail and downstream pipelines can address it by this value.")
	_ = trailsCreateCmd.MarkFlagRequired("flow-id")
	_ = trailsCreateCmd.MarkFlagRequired("commit")
	_ = trailsCreateCmd.MarkFlagRequired("branch")
	_ = trailsCreateCmd.MarkFlagRequired("author")
	_ = trailsCreateCmd.MarkFlagRequired("author-email")

	trailsLookupCmd.Flags().StringVar(&trailLookupFlowID, "flow-id", "", "Flow ID (required)")
	trailsLookupCmd.Flags().StringVar(&trailLookupExternalID, "external-id", "", "Release identifier to resolve")
	trailsLookupCmd.Flags().StringVar(&trailLookupName, "name", "", "Trail name to resolve")
	trailsLookupCmd.Flags().StringVar(&trailLookupCommit, "commit", "", "Git commit SHA (resolves the most recent run)")
	_ = trailsLookupCmd.MarkFlagRequired("flow-id")

	trailsCmd.AddCommand(trailsListCmd, trailsGetCmd, trailsCreateCmd, trailsLookupCmd)
}
