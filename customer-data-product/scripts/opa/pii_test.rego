package datamesh.customer.pii_test

import data.datamesh.customer.pii

test_admin_sees_everything if {
	pii.masked == {} with input as {"user": "a", "roles": ["admin"]}
}

test_steward_sees_everything if {
	pii.masked == {} with input as {"user": "a", "roles": ["data-steward", "support-agent"]}
}

test_support_masks_contact_data if {
	m := pii.masked with input as {"user": "s", "roles": ["support-agent"]}
	m.Customer.emailAddress == "partial"
	m.Address.street == "redact"
	not m.Customer.firstName
}

test_unknown_role_is_restricted if {
	m := pii.masked with input as {"user": "x", "roles": ["analyst"]}
	m.Customer.emailAddress == "redact"
	m.Customer.firstName == "partial"
}

test_missing_roles_is_restricted if {
	m := pii.masked with input as {"user": "x"}
	m.Phone.number == "redact"
}
