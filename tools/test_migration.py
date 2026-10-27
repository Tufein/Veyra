import unittest
from export_legacy_data import convert

class LegacyMigrationTest(unittest.TestCase):
    def test_defaults_and_no_credentials(self):
        result = convert(b'<map><string name="password">never</string><string name="nickname">Name</string></map>')
        self.assertEqual(4, len(result['shortcuts']))
        self.assertEqual({'nickname': 'Name'}, result['preferences'])
        self.assertNotIn('password', str(result))
    def test_existing_websites_survive(self):
        raw = b'<map><string name="bookmarks">[{"title":"Saved","url":"https://example.org/"}]</string></map>'
        self.assertEqual('Saved', convert(raw)['bookmarks'][0]['title'])
    def test_unsafe_and_malformed_input_fails(self):
        for raw in (b'broken', b'<!DOCTYPE map><map/>', b'<map><string name="bookmarks">[{"title":"Bad","url":"javascript:alert(1)"}]</string></map>'):
            with self.assertRaises(Exception):
                convert(raw)
    def test_normal_tabs_are_opt_in_and_private_tabs_are_rejected(self):
        raw = b'<map><string name="tabs">[{"url":"https://example.org/","title":"Saved","home":false}]</string></map>'
        self.assertNotIn('tabs', convert(raw))
        self.assertEqual('https://example.org/', convert(raw, True)['tabs'][0]['url'])
        with self.assertRaises(ValueError):
            convert(b'<map><string name="tabs">[{"url":"https://private.example/","private":true}]</string></map>', True)
    def test_original_false_values_remain_false(self):
        result = convert(b'<map><boolean name="blocking" value="false"/><int name="text_zoom" value="115"/></map>')
        self.assertFalse(result['preferences']['blocking'])
        self.assertEqual(115, result['preferences']['text_zoom'])

if __name__ == '__main__':
    unittest.main()
