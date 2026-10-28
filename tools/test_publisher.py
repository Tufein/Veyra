import unittest
from publish_prebuilt import validate_assets, find_release


class PublisherTests(unittest.TestCase):
    def test_finds_an_unpublished_draft_in_paginated_release_lists(self):
        draft={'id':123,'tag_name':'v1.0.0-rc.1','draft':True}
        self.assertEqual(draft,find_release([[{'tag_name':'v0.5.0'}],[draft]],'v1.0.0-rc.1'))
        self.assertIsNone(find_release([[draft]],'v1.0.0'))

    def test_matching_uploaded_files_can_be_resumed_without_replacement(self):
        self.assertEqual(['two.apk'], validate_assets([
            {'name': 'one.apk', 'state': 'uploaded', 'digest': 'sha256:'+'ab'*32}],
            {'one.apk': 'ab'*32, 'two.apk': 'cd'*32}))

    def test_different_existing_bytes_cannot_be_overwritten(self):
        with self.assertRaises(ValueError):
            validate_assets([{'name': 'one.apk', 'state': 'uploaded', 'digest': 'sha256:'+'cd'*32}], {'one.apk': 'ab'*32})

    def test_partial_or_unexpected_assets_block_publication(self):
        for asset in [{'name': 'one.apk', 'state': 'starter', 'digest': 'sha256:'+'ab'*32},
                      {'name': 'foreign.apk', 'state': 'uploaded', 'digest': 'sha256:'+'ab'*32}]:
            with self.assertRaises(ValueError): validate_assets([asset], {'one.apk': 'ab'*32})

    def test_duplicate_assets_do_not_count_as_complete(self):
        asset = {'name': 'one.apk', 'state': 'uploaded', 'digest': 'sha256:'+'ab'*32}
        with self.assertRaises(ValueError): validate_assets([asset, asset], {'one.apk': 'ab'*32})
