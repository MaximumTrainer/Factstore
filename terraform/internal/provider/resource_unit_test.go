package provider

import (
	"context"
	"testing"

	"github.com/hashicorp/terraform-plugin-framework/attr"
	"github.com/hashicorp/terraform-plugin-framework/diag"
	"github.com/hashicorp/terraform-plugin-framework/resource"
	"github.com/hashicorp/terraform-plugin-framework/types"
)

// ---- setFlowState tests ----

func TestSetFlowState_AllFieldsMapped(t *testing.T) {
	flow := &FlowResponse{
		ID:                      "flow-abc",
		Name:                    "my-flow",
		Description:             "some description",
		RequiredAttestationTypes: []string{"junit", "snyk"},
		CreatedAt:               "2024-01-01T00:00:00Z",
		UpdatedAt:               "2024-01-02T00:00:00Z",
	}

	var state FlowResourceModel
	diags := setFlowState(&state, flow)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}

	if state.ID.ValueString() != "flow-abc" {
		t.Errorf("ID: got %q, want %q", state.ID.ValueString(), "flow-abc")
	}
	if state.Name.ValueString() != "my-flow" {
		t.Errorf("Name: got %q, want %q", state.Name.ValueString(), "my-flow")
	}
	if state.Description.ValueString() != "some description" {
		t.Errorf("Description: got %q, want %q", state.Description.ValueString(), "some description")
	}
	if state.CreatedAt.ValueString() != "2024-01-01T00:00:00Z" {
		t.Errorf("CreatedAt: got %q", state.CreatedAt.ValueString())
	}
	if state.UpdatedAt.ValueString() != "2024-01-02T00:00:00Z" {
		t.Errorf("UpdatedAt: got %q", state.UpdatedAt.ValueString())
	}

	var attestationTypes []string
	diags = state.RequiredAttestationTypes.ElementsAs(context.Background(), &attestationTypes, false)
	if diags.HasError() {
		t.Fatalf("extracting attestation types: %v", diags)
	}
	if len(attestationTypes) != 2 || attestationTypes[0] != "junit" || attestationTypes[1] != "snyk" {
		t.Errorf("RequiredAttestationTypes: got %v, want [junit snyk]", attestationTypes)
	}
}

func TestSetFlowState_EmptyAttestationTypes(t *testing.T) {
	flow := &FlowResponse{
		ID:                      "f1",
		Name:                    "flow",
		RequiredAttestationTypes: []string{},
		CreatedAt:               "2024-01-01T00:00:00Z",
		UpdatedAt:               "2024-01-01T00:00:00Z",
	}

	var state FlowResourceModel
	diags := setFlowState(&state, flow)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if state.RequiredAttestationTypes.IsNull() {
		t.Error("RequiredAttestationTypes should not be null for empty slice")
	}
	if state.RequiredAttestationTypes.IsUnknown() {
		t.Error("RequiredAttestationTypes should not be unknown for empty slice")
	}
	if len(state.RequiredAttestationTypes.Elements()) != 0 {
		t.Errorf("expected 0 elements, got %d", len(state.RequiredAttestationTypes.Elements()))
	}
}

// ---- listToStringSlice tests ----

func TestListToStringSlice_Normal(t *testing.T) {
	ctx := context.Background()
	var diags diag.Diagnostics
	list, d := types.ListValue(types.StringType, []attr.Value{
		types.StringValue("a"),
		types.StringValue("b"),
	})
	diags.Append(d...)
	if diags.HasError() {
		t.Fatalf("building list: %v", diags)
	}

	result := listToStringSlice(ctx, list, &diags)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if len(result) != 2 || result[0] != "a" || result[1] != "b" {
		t.Errorf("got %v, want [a b]", result)
	}
}

func TestListToStringSlice_NullList(t *testing.T) {
	ctx := context.Background()
	var diags diag.Diagnostics
	list := types.ListNull(types.StringType)

	result := listToStringSlice(ctx, list, &diags)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if len(result) != 0 {
		t.Errorf("expected empty slice for null list, got %v", result)
	}
}

func TestListToStringSlice_UnknownList(t *testing.T) {
	ctx := context.Background()
	var diags diag.Diagnostics
	list := types.ListUnknown(types.StringType)

	result := listToStringSlice(ctx, list, &diags)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if len(result) != 0 {
		t.Errorf("expected empty slice for unknown list, got %v", result)
	}
}

func TestListToStringSlice_EmptyList(t *testing.T) {
	ctx := context.Background()
	var diags diag.Diagnostics
	list, d := types.ListValue(types.StringType, []attr.Value{})
	diags.Append(d...)
	if diags.HasError() {
		t.Fatalf("building empty list: %v", diags)
	}

	result := listToStringSlice(ctx, list, &diags)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if len(result) != 0 {
		t.Errorf("expected empty slice, got %v", result)
	}
}

// ---- FlowResource schema/metadata tests ----

func TestFlowResource_Metadata(t *testing.T) {
	r := &FlowResource{}
	req := resource.MetadataRequest{ProviderTypeName: "factstore"}
	var resp resource.MetadataResponse
	r.Metadata(context.Background(), req, &resp)
	if resp.TypeName != "factstore_flow" {
		t.Errorf("TypeName: got %q, want %q", resp.TypeName, "factstore_flow")
	}
}

func TestFlowResource_Schema_RequiredAttributes(t *testing.T) {
	r := &FlowResource{}
	var resp resource.SchemaResponse
	r.Schema(context.Background(), resource.SchemaRequest{}, &resp)
	if resp.Diagnostics.HasError() {
		t.Fatalf("schema diagnostics: %v", resp.Diagnostics)
	}

	attrs := resp.Schema.Attributes
	requiredAttr := []string{"name"}
	for _, name := range requiredAttr {
		a, ok := attrs[name]
		if !ok {
			t.Errorf("missing required attribute %q in schema", name)
			continue
		}
		if !a.IsRequired() {
			t.Errorf("attribute %q should be required", name)
		}
	}

	computedAttrs := []string{"id", "created_at", "updated_at"}
	for _, name := range computedAttrs {
		a, ok := attrs[name]
		if !ok {
			t.Errorf("missing computed attribute %q in schema", name)
			continue
		}
		if !a.IsComputed() {
			t.Errorf("attribute %q should be computed", name)
		}
	}
}

// ---- setPolicyState tests ----

func TestSetPolicyState_AllFieldsMapped(t *testing.T) {
	policy := &PolicyResponse{
		ID:                      "pol-abc",
		Name:                    "prod-policy",
		EnforceProvenance:       true,
		EnforceTrailCompliance:  false,
		RequiredAttestationTypes: []string{"snyk"},
		CreatedAt:               "2024-01-01T00:00:00Z",
		UpdatedAt:               "2024-01-01T00:00:00Z",
	}

	var state PolicyResourceModel
	diags := setPolicyState(&state, policy)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if state.ID.ValueString() != "pol-abc" {
		t.Errorf("ID: got %q, want %q", state.ID.ValueString(), "pol-abc")
	}
	if state.Name.ValueString() != "prod-policy" {
		t.Errorf("Name: got %q, want %q", state.Name.ValueString(), "prod-policy")
	}
	if !state.EnforceProvenance.ValueBool() {
		t.Error("EnforceProvenance should be true")
	}
	if state.EnforceTrailCompliance.ValueBool() {
		t.Error("EnforceTrailCompliance should be false")
	}

	var attestationTypes []string
	diags = state.RequiredAttestationTypes.ElementsAs(context.Background(), &attestationTypes, false)
	if diags.HasError() {
		t.Fatalf("extracting attestation types: %v", diags)
	}
	if len(attestationTypes) != 1 || attestationTypes[0] != "snyk" {
		t.Errorf("RequiredAttestationTypes: got %v, want [snyk]", attestationTypes)
	}
}

func TestSetPolicyState_EmptyAttestationTypes(t *testing.T) {
	policy := &PolicyResponse{
		ID:                      "p2",
		Name:                    "empty-pol",
		RequiredAttestationTypes: []string{},
		CreatedAt:               "2024-01-01T00:00:00Z",
		UpdatedAt:               "2024-01-01T00:00:00Z",
	}

	var state PolicyResourceModel
	diags := setPolicyState(&state, policy)
	if diags.HasError() {
		t.Fatalf("unexpected diagnostics: %v", diags)
	}
	if state.RequiredAttestationTypes.IsNull() || state.RequiredAttestationTypes.IsUnknown() {
		t.Error("RequiredAttestationTypes should be a known empty list")
	}
	if len(state.RequiredAttestationTypes.Elements()) != 0 {
		t.Errorf("expected 0 elements, got %d", len(state.RequiredAttestationTypes.Elements()))
	}
}
