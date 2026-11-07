# Field-level PII masking policy for the datamesh-customer GraphQL API.
#
# Input:  {"user": "alice", "roles": ["support-agent"]}
# Output: {type: {field: strategy}} for every field that must be masked.
#         Strategies: "redact" -> "***", "partial" -> keep a hint (j***@acme.com),
#                     "null"   -> return null.
# Anything not listed is returned in clear text.
#
# Query: POST /v1/data/datamesh/customer/pii/masked
package datamesh.customer.pii

# Roles that may see everything.
full_access_roles := {"admin", "data-steward"}

# The profile is derived from the roles; unknown or missing roles are restricted.
profile := "full" if {
	some role in input.roles
	role in full_access_roles
} else := "support" if {
	"support-agent" in input.roles
} else := "restricted"

profiles := {
	"full": {},
	"support": {
		"Customer": {
			"emailAddress": "partial",
			"phoneNumber": "partial",
		},
		"Address": {
			"street": "redact",
			"zipCode": "redact",
		},
	},
	"restricted": {
		"Customer": {
			"firstName": "partial",
			"lastName": "partial",
			"emailAddress": "redact",
			"phoneNumber": "redact",
		},
		"Address": {
			"street": "redact",
			"zipCode": "redact",
		},
	},
}

masked := profiles[profile]
