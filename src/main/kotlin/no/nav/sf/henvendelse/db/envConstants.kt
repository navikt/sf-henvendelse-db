package no.nav.sf.henvendelse.db

/**
 * Naming convention applied to environment variable constants: a lowercase prefix separated from the actual constant, i.e. prefix_ENVIRONMENT_VARIABLE_NAME.
 *
 * Motivation:
 * The prefix provides contextual naming that describes the source and nature of the variables they represent while keeping the names short.
 * A prefix marks a constant representing an environment variable, and also where one can find the value of that variable
 *
 * - env: Denotes an environment variable typically injected into the pod by the Nais platform.
 *
 * - config: Denotes an environment variable explicitly configured in YAML files (see dev.yaml, prod.yaml)
 *
 * - secret: Denotes an environment variable loaded from a Kubernetes secret.
 */
const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

const val config_VIEW_PAGE_SIZE = "VIEW_PAGE_SIZE"
const val config_CONTEXT = "CONTEXT"

const val env_REDIS_URI_HENVENDELSELISTE = "REDIS_URI_HENVENDELSELISTE"
const val env_VALKEY_URI_HENVENDELSELISTE = "VALKEY_URI_HENVENDELSELISTE"
const val env_VALKEY_HOST_HENVENDELSELISTE = "VALKEY_HOST_HENVENDELSELISTE"
const val env_VALKEY_PORT_HENVENDELSELISTE = "VALKEY_PORT_HENVENDELSELISTE"
const val env_VALKEY_USERNAME_HENVENDELSELISTE = "VALKEY_USERNAME_HENVENDELSELISTE"
const val env_VALKEY_PASSWORD_HENVENDELSELISTE = "VALKEY_PASSWORD_HENVENDELSELISTE"

/**
 * Shortcuts for fetching environment variables
 */
fun env(name: String): String = System.getenv(name) ?: throw NullPointerException("Missing env $name")
