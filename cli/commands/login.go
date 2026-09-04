package commands

import (
	"errors"
	"fmt"
	"strings"

	"github.com/MaximumTrainer/Factstore/cli/internal/client"

	"github.com/MaximumTrainer/Factstore/cli/internal/output"
	"github.com/MaximumTrainer/Factstore/cli/pkg/api"
	"github.com/spf13/cobra"
)

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Verify connectivity to the Factstore API",
	Long:  "Test the configured host and token by listing flows. Prints success or an error.",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := newClient()
		if err != nil {
			return err
		}
		// Ask the server who we are. /auth/me answers for a session and for an API key, and
		// tells us the scopes, which is the useful thing to print.
		principal, err := api.WhoAmI(c)
		if err != nil {
			// "authentication or connectivity failed" for both was the old message; a wrong
			// key and an unreachable host need different fixes.
			var apiErr *client.APIError
			if errors.As(err, &apiErr) {
				if guidance := apiErr.Guidance(); guidance != "" {
					return fmt.Errorf("%s\n\n  %s", apiErr.Error(), guidance)
				}
				return apiErr
			}
			return fmt.Errorf("could not reach %s: %w\n\n  Check the host with 'factstore configure', "+
				"and that the API is reachable from here", c.BaseURL, err)
		}

		output.PrintSuccess(fmt.Sprintf("Connected to %s", c.BaseURL))
		rows := [][]string{
			{"Type", principal.Type},
		}
		if principal.Email != "" {
			rows = append(rows, []string{"Identity", principal.Email})
		}
		if principal.OwnerID != "" {
			rows = append(rows, []string{"Key owner", principal.OwnerID})
		}
		if principal.OrgSlug != "" {
			rows = append(rows, []string{"Organisation", principal.OrgSlug})
		}
		if principal.Role != "" {
			rows = append(rows, []string{"Role", principal.Role})
		}
		rows = append(rows, []string{"Permissions", strings.Join(principal.Permissions, ", ")})
		output.PrintTable([]string{"FIELD", "VALUE"}, rows)
		return nil
	},
}
