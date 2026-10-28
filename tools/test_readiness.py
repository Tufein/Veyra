import unittest
from release_readiness import REQUIRED, assess


class ReadinessTests(unittest.TestCase):
    def setUp(self):
        self.report = {'schema': 1, 'gates': {name: {'status': 'pending', 'evidence': None} for name in REQUIRED}}

    def test_pending_hardware_does_not_allow_a_stable_launch(self):
        result = assess('v1.0.0', self.report)
        self.assertFalse(result['publication_allowed'])
        self.assertFalse(result['prerelease'])

    def test_candidate_can_ship_without_claiming_stable_readiness(self):
        result = assess('v1.0.0-rc.1', self.report)
        self.assertTrue(result['publication_allowed'])
        self.assertTrue(result['prerelease'])
        self.assertFalse(result['stable_ready'])
        self.assertEqual(list(REQUIRED), result['pending'])

    def test_a_checked_box_without_evidence_does_not_satisfy_a_gate(self):
        self.report['gates']['signed_update']['status'] = 'passed'
        with self.assertRaises(ValueError):
            assess('v1.0.0', self.report)

    def test_dropping_a_required_gate_cannot_make_a_report_pass(self):
        del self.report['gates']['privacy_storage']
        with self.assertRaises(ValueError):
            assess('v1.0.0', self.report)

    def test_evidenced_report_allows_stable_without_changing_tag_semantics(self):
        for gate in self.report['gates'].values():
            gate.update(status='passed', evidence='https://example.org/test-fixture/evidence')
        result = assess('v1.0.0', self.report)
        self.assertTrue(result['publication_allowed'])
        self.assertFalse(result['prerelease'])

    def test_arbitrary_tag_text_is_rejected(self):
        for tag in ['v1.0.0/other', 'v1.0.0-unknown.1', '../v1.0.0']:
            with self.assertRaises(ValueError):
                assess(tag, self.report)
