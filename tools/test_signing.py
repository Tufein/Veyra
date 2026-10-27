import unittest
from apk_certificate import certificate_digest


class CertificateOutputTests(unittest.TestCase):
    def test_legacy_output(self):
        self.assertEqual('ab'*32, certificate_digest('Signer #1 certificate SHA-256 digest: '+ 'AB'*32))

    def test_sdk37_output_and_duplicate_scheme(self):
        output='V2 Signer: certificate SHA-256 digest: '+ 'ab'*32+'\nV3 Signer: certificate SHA-256 digest: '+ 'ab'*32+'\n'
        self.assertEqual('ab'*32,certificate_digest(output))

    def test_multiple_keys_and_missing_identity_are_rejected(self):
        for output in ('Verified', 'Signer #1 certificate SHA-256 digest: '+ 'ab'*32+'\nSigner #2 certificate SHA-256 digest: '+ 'cd'*32):
            with self.assertRaises(ValueError):certificate_digest(output)
