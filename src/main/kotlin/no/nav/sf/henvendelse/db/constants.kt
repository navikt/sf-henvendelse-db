package no.nav.sf.henvendelse.db

/**
 * Naming convention applied to environment variable constants: a lowercase prefix separated from the actual constant prefix_CONSTANT_VARIABLE.
 *
 * Motivation:
 * The prefix provides contextual naming that describes the source and nature of the variables they represent while keeping the names short.
 *
 * - env: Denotes an environment variable typically injected into the pod by the Nais platform.
 *
 * - config: Denotes an environment variable explicitly configured in YAML files (see dev.yaml, prod.yaml).
 *
 * - secret: Denotes an environment variable loaded from a Kubernetes secret.
 */
// Environment variables injected in pod:
const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

// Environment variables set as yaml config:
const val config_VIEW_PAGE_SIZE = "VIEW_PAGE_SIZE"
const val config_CONTEXT = "CONTEXT"
