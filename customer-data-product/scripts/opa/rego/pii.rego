# Field-level PII masking policy for the datamesh-customer GraphQL API.
#
# Input:  {"user": "alice", "roles": ["data-steward"]}
# Output: {type: {field: "redact"}} for every field that must be masked ("redact" -> "***").
#         Anything not listed is returned in clear text.
#
# Which fields are sensitive comes from the data contract: pii-classification-data.json is
# generated from the ODCS contract customer-dp-graphql.odcs.yaml (see templates/), it holds
# {type: {field: classification}}. Fields classified "confidential" are masked unless the
# caller has a full access role. Missing or unknown roles are restricted.
#
# Query: POST /v1/data/datamesh/customer/pii/masked
package datamesh.customer.pii

# Roles that may see confidential fields.
full_access_roles := {"admin", "data-steward"}

# Classification that must be masked.
masked_classification := "confidential"

full_access if {
	some role in input.roles
	role in full_access_roles
}

masked := {} if {
	full_access
} else := {type: {field: "redact" |
	some field, classification in fields
	classification == masked_classification
} |
	some type, fields in data.datamesh.customer.pii.classification
}

