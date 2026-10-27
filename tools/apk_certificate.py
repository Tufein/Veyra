"""Read the single certificate identity from current and legacy apksigner output."""
import re


def certificate_digest(output):
    values = re.findall(r'^(?:Signer #\d+|V[1-4] Signer(?: #\d+)?):? certificate SHA-256 digest: ([a-fA-F0-9]{64})\s*$', output, re.MULTILINE)
    digests = {value.lower() for value in values}
    if len(digests) != 1:
        raise ValueError('Expected exactly one verified APK certificate identity')
    return digests.pop()
