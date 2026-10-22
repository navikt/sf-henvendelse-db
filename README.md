# sf-henvendelse-db [<img align="right" src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" width="18" alt="GitHub repository" />](https://github.com/navikt/sf-henvendelse-db)

En cache for henvendelser knyttet til Salesforce. Cache-nøkkelen er `(aktorId, page, pageSize, version)`.

[sf-henvendelse-api-proxy](https://github.com/navikt/sf-henvendelse-api-proxy) bruker cachen til å svare på forespørsler om henvendelseslister. Når proxyen henter data fra Salesforce, oppdateres cachen med resultatet.

Når proxyen eller Salesforce utfører operasjoner som gjør et cache-element utdatert, kan den tilhørende oppføringen slettes. Da hentes oppdaterte data fra Salesforce ved neste behov.
