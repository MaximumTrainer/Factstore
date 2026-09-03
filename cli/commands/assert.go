package commands

import (
	"fmt"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var (
	assertSha256  string
	assertFlowID  string
	assertTrailID string
)

var assertCmd = &cobra.Command{
	Use:   "assert",
	Short: "Assert compliance for an artifact against a flow",
	Long: "Check whether the artifact identified by SHA-256 meets all attestation requirements of the given flow.\n\n" +
		"Pass --trail-id to judge one specific pipeline execution. CI pipelines should always do so: without it,\n" +
		"the most recent trail carrying the digest decides, which may not be the run you are asserting.\n" +
		"With --trail-id and no --sha256, the trail is judged on its own attestations, which is the right call\n" +
		"for gates that run before the image is pushed.",
	RunE: func(cmd *cobra.Command, args []string) error {
		if assertSha256 == "" && assertTrailID == "" {
			return fmt.Errorf("--sha256 or --trail-id is required")
		}
		if assertFlowID == "" && assertTrailID == "" {
			return fmt.Errorf("--flow-id is required")
		}
		c, err := newClient()
		if err != nil {
			return err
		}

		var result *api.AssertResponse
		if assertSha256 == "" {
			// Trail-scoped with no digest: judge the execution on its own evidence.
			result, err = api.AssertTrail(c, assertTrailID, api.TrailAssertRequest{FlowID: assertFlowID})
		} else {
			result, err = api.Assert(c, api.AssertRequest{
				Sha256Digest: assertSha256,
				FlowID:       assertFlowID,
				TrailID:      assertTrailID,
			})
		}
		if err != nil {
			return err
		}
		if jsonOutput {
			output.PrintJSON(result)
			return nil
		}
		output.PrintTable(
			[]string{"FIELD", "VALUE"},
			assertResultRows(result),
		)
		return nil
	},
}

// assertResultRows renders a verdict, showing whichever of the type-based or
// name-based attestation lists the flow actually uses.
func assertResultRows(result *api.AssertResponse) [][]string {
	missing := result.MissingAttestationTypes
	failed := result.FailedAttestationTypes
	if len(missing) == 0 {
		missing = result.MissingAttestationNames
	}
	if len(failed) == 0 {
		failed = result.FailedAttestationNames
	}
	return [][]string{
		{"SHA256", result.Sha256Digest},
		{"Flow ID", result.FlowID},
		{"Trail ID", result.TrailID},
		{"Status", result.Status},
		{"Missing Attestations", strings.Join(missing, ", ")},
		{"Failed Attestations", strings.Join(failed, ", ")},
		{"Details", result.Details},
	}
}

func init() {
	assertCmd.Flags().StringVar(&assertSha256, "sha256", "", "SHA-256 digest of the artifact")
	assertCmd.Flags().StringVar(&assertFlowID, "flow-id", "", "Flow ID to assert against (defaults to the trail's flow when --trail-id is given)")
	assertCmd.Flags().StringVar(&assertTrailID, "trail-id", "", "Scope the assertion to a single pipeline execution")
}
