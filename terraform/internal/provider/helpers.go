package provider

import (
	"context"

	"github.com/hashicorp/terraform-plugin-framework/diag"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

// listToStringSlice converts a types.List of strings to a Go string slice.
func listToStringSlice(ctx context.Context, list types.List, diagnostics *diag.Diagnostics) []string {
	if list.IsNull() || list.IsUnknown() {
		return []string{}
	}
	var items []string
	diagnostics.Append(list.ElementsAs(ctx, &items, false)...)
	if items == nil {
		return []string{}
	}
	return items
}
