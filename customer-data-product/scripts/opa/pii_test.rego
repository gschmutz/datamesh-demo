package datamesh.customer.pii_test

import data.datamesh.customer.pii

# Same shape as the generated pii-classification.json.
classification := {
	"Customer": {"firstName": "confidential", "emailAddress": "confidential", "segment": "internal"},
	"Address": {"street": "confidential"},
	"Phone": {"number": "confidential"},
}

test_admin_sees_everything if {
	pii.masked == {} with input as {"user": "a", "roles": ["admin"]}
		with data.datamesh.customer.pii.classification as classification
}

test_steward_sees_everything if {
	pii.masked == {} with input as {"user": "a", "roles": ["data-steward", "support-agent"]}
		with data.datamesh.customer.pii.classification as classification
}

test_unknown_role_masks_confidential_fields if {
	m := pii.masked with input as {"user": "x", "roles": ["analyst"]}
		with data.datamesh.customer.pii.classification as classification
	m == {
		"Customer": {"firstName": "redact", "emailAddress": "redact"},
		"Address": {"street": "redact"},
		"Phone": {"number": "redact"},
	}
}

test_support_agent_is_not_privileged if {
	m := pii.masked with input as {"user": "s", "roles": ["support-agent"]}
		with data.datamesh.customer.pii.classification as classification
	m.Customer.emailAddress == "redact"
}

test_missing_roles_is_restricted if {
	m := pii.masked with input as {"user": "x"}
		with data.datamesh.customer.pii.classification as classification
	m.Phone.number == "redact"
}

test_only_confidential_is_masked if {
	m := pii.masked with input as {"user": "x", "roles": []}
		with data.datamesh.customer.pii.classification as classification
	not m.Customer.segment
}

test_new_classified_field_is_masked_without_policy_change if {
	m := pii.masked with input as {"user": "x", "roles": []}
		with data.datamesh.customer.pii.classification as object.union(classification, {"Customer": {"dateOfBirth": "confidential"}})
	m.Customer.dateOfBirth == "redact"
}
