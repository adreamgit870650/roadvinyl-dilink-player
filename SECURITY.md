# Security

RoadVinyl is an experimental vehicle infotainment application and has not undergone an
independent security audit.

## Safe deployment

- Test builds on a non-critical device while the vehicle is parked.
- Sign releases with a protected keystore that is never committed to Git.
- Configure only HTTPS update endpoints that you control.
- Publish an APK only after recording its exact byte size and SHA-256 digest.
- Review third-party online music, lyric, radio, podcast, and cover-art endpoints before enabling
  them in your environment.
- Do not commit CloudBase environment IDs, object IDs, access tokens, certificates, private
  domains, user media, or device data.

Security reports may be sent to `95588577@qq.com`. Please avoid including live credentials or
personal media in a report.
