import importlib.util
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("mirror", Path(__file__).resolve().parents[1] / "deploy/offline/mirror.py")
mirror = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mirror)


class DeliveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        (self.bundle / "imageset.yaml").write_text("{}")
        (self.bundle / "mirror_000001.tar").write_bytes(b"oc-mirror fixture")
        self.data = {"schema": 1, "version": "4.0.1", "arch": "arm64",
                     "tool": mirror.TOOL_IMAGE + "-arm64", "registry": "ontomato-offline-registry:3.1.2-arm64",
                     "images": {key: "example.com/product/" + key.lower() + "@sha256:" + "a" * 64 for key in mirror.IMAGE_KEYS},
                     "files": {p.name: {"bytes": p.stat().st_size, "sha256": mirror.sha256(p)} for p in self.bundle.iterdir()}}
        self.write_manifest()

    def write_manifest(self):
        (self.bundle / "delivery.json").write_text(json.dumps(self.data))

    def test_corrupt_archive_fails_before_docker(self):
        (self.bundle / "mirror_000001.tar").write_bytes(b"corrupt")
        with patch.object(mirror, "run") as run, self.assertRaisesRegex(ValueError, "checksum"):
            mirror.import_bundle(SimpleNamespace(bundle=self.bundle, state=self.root / "state"))
        run.assert_not_called()

    def test_paths_cannot_escape_delivery(self):
        self.data["files"]["../secret"] = {"bytes": 0, "sha256": ""}
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            mirror.verify_bundle(self.bundle)

    def test_failed_import_preserves_active_references(self):
        target = self.root / "images.env"
        target.write_text("WORKBENCH_IMAGE=previous\n")
        args = SimpleNamespace(bundle=self.bundle, state=self.root / "state", port=5000,
                               project="fixture", output_env=target)
        with patch.object(mirror, "run"), patch.object(mirror, "ensure_mirror_network"), patch.object(mirror, "mirror", side_effect=ValueError("missing baseline")):
            with self.assertRaisesRegex(ValueError, "baseline"):
                mirror.import_bundle(args)
        self.assertEqual(target.read_text(), "WORKBENCH_IMAGE=previous\n")
        self.assertFalse((self.root / "state/imports").exists())

    def test_successful_import_outputs_digests_and_keeps_history(self):
        state = self.root / "state"
        history = state / "incoming/working-dir/.history"
        history.mkdir(parents=True)
        (history / "previous").write_text("keep")
        (state / "incoming/mirror_999999.tar").write_text("old")
        target = self.root / "images.env"
        args = SimpleNamespace(bundle=self.bundle, state=state, port=5100,
                               project="fixture", output_env=target)
        with patch.object(mirror, "run"), patch.object(mirror, "ensure_mirror_network"), patch.object(mirror, "mirror") as invoke:
            mirror.import_bundle(args)
        self.assertEqual((history / "previous").read_text(), "keep")
        self.assertEqual(len(list((state / "imports").glob("*.json"))), 1)
        self.assertFalse((state / "incoming/mirror_999999.tar").exists())
        self.assertIn("WORKBENCH_IMAGE=127.0.0.1:5100/product/workbench_image@sha256:" + "a" * 64, target.read_text())
        self.assertIn("docker://registry:5000", invoke.call_args[0][2])

    def test_registry_is_attached_to_the_mirror_network_when_compose_left_it_out(self):
        with patch.object(mirror, "run", side_effect=["cid", json.dumps({"p_default": {}}), None]) as run:
            mirror.ensure_mirror_network("p", {})
        self.assertEqual(run.call_args_list[-1].args[0], ["docker", "network", "connect", "--alias", "registry", "p_mirror", "cid"])

    def test_registry_already_on_the_mirror_network_is_left_alone(self):
        with patch.object(mirror, "run", side_effect=["cid", json.dumps({"p_default": {}, "p_mirror": {}})]) as run:
            mirror.ensure_mirror_network("p", {})
        self.assertEqual(run.call_count, 2)

    def test_source_repository_preserves_ports_and_nested_paths(self):
        self.assertEqual(mirror.repository("localhost:5000/a/b:v1"), "localhost:5000/a/b")
        self.assertEqual(mirror.repository("example.com/a/b@sha256:" + "a" * 64), "example.com/a/b")
        with self.assertRaisesRegex(ValueError, "fully qualified"):
            mirror.repository("pgvector/pgvector:pg18")

    def test_resolve_selects_platform_digest_without_product_policy(self):
        digest = "sha256:" + "b" * 64
        manifest = {"manifests": [
            {"platform": {"os": "linux", "architecture": "amd64"}, "digest": "sha256:" + "a" * 64},
            {"platform": {"os": "linux", "architecture": "arm64"}, "digest": digest},
        ]}
        image = {"Os": "linux", "Architecture": "arm64"}
        with patch.object(mirror.subprocess, "check_output", return_value=json.dumps(manifest).encode()), \
                patch.object(mirror, "run", side_effect=["pull output", json.dumps([image])]) as run:
            ref = mirror.resolve_image("example.com/arbitrary/image:mutable", "arm64")
        self.assertEqual(ref, "example.com/arbitrary/image@" + digest)
        self.assertEqual(run.call_args_list[0].args[0], ["docker", "pull", "--platform", "linux/arm64", ref])

    def test_build_tag_survives_resolution_and_offline_import_with_the_same_digest(self):
        raw = b'{"schemaVersion":2}\n'
        digest = "sha256:" + hashlib.sha256(raw).hexdigest()
        build = "4.1.0-dev-ci123-gabc"
        image = {"Os": "linux", "Architecture": "arm64", "Config": {"Labels": {
            "org.opencontainers.image.version": "4.1.0-dev", "io.ontomato.build": build}}}
        with patch.object(mirror.subprocess, "check_output", return_value=raw), \
                patch.object(mirror, "run", side_effect=["", json.dumps([image])]):
            ref = mirror.resolve_image("example.com/a:edge", "arm64")
        self.assertEqual(ref, "example.com/a:" + build + "@" + digest)
        self.data["images"]["WORKBENCH_IMAGE"] = ref
        self.write_manifest()
        target = self.root / "images.env"
        args = SimpleNamespace(bundle=self.bundle, state=self.root / "state", port=5000,
                               project="fixture", output_env=target)
        with patch.object(mirror, "run"), patch.object(mirror, "ensure_mirror_network"), patch.object(mirror, "mirror"):
            mirror.import_bundle(args)
        self.assertIn("WORKBENCH_IMAGE=127.0.0.1:5000/a:" + build + "@" + digest, target.read_text())

    def test_resolve_single_manifest_hashes_exact_bytes(self):
        raw = b'{"schemaVersion":2}\n'
        image = {"Os": "linux", "Architecture": "arm64"}
        with patch.object(mirror.subprocess, "check_output", return_value=raw), \
                patch.object(mirror, "run", side_effect=["", json.dumps([image])]):
            ref = mirror.resolve_image("example.com/a:tag", "arm64")
        self.assertEqual(ref, "example.com/a@sha256:" + hashlib.sha256(raw).hexdigest())

    def test_resolve_rejects_wrong_platform(self):
        with patch.object(mirror.subprocess, "check_output", return_value=b'{"schemaVersion":2}'), \
                patch.object(mirror, "run", side_effect=["", json.dumps([{"Os": "linux", "Architecture": "amd64"}])]):
            with self.assertRaisesRegex(ValueError, "platform"):
                mirror.resolve_image("example.com/a:tag", "arm64")

    def export_args(self):
        refs = list(self.data["images"].values())
        return SimpleNamespace(state=self.root / "state", output=self.root / "exported", arch="arm64",
                               version="4.0.1", workbench=refs[0], data_engine=refs[1], manager=refs[2],
                               postgres=refs[3], bootstrap=False, insecure_source=False, network=None, authfile=None,
                               registry_repository=mirror.REGISTRY_REPOSITORY)

    def test_export_rejects_mutable_tag_before_creating_state(self):
        args = self.export_args()
        args.workbench = "example.com/a:mutable"
        with patch.object(mirror, "ensure_tool") as tool, self.assertRaisesRegex(ValueError, "immutable"):
            mirror.export(args)
        tool.assert_not_called()
        self.assertFalse(args.state.exists())

    def test_export_preserves_validated_pins_without_resolving_again(self):
        args = self.export_args()
        def write_archive(*_):
            (args.state / "export").mkdir()
            (args.state / "export/mirror_000001.tar").write_bytes(b"archive")
        with patch.object(mirror, "resolve_image") as resolve, \
                patch.object(mirror, "engine_arch", return_value="arm64"), \
                patch.object(mirror, "ensure_tool", return_value=self.data["tool"]), \
                patch.object(mirror, "mirror", side_effect=write_archive):
            mirror.export(args)
        resolve.assert_not_called()
        data = mirror.verify_bundle(args.output)
        self.assertEqual(data["images"], self.data["images"])
        names = [item["name"] for item in json.loads((args.output / "imageset.yaml").read_text())["mirror"]["additionalImages"]]
        self.assertEqual(len(names), 4)
        self.assertTrue(all(name.endswith("@sha256:" + "a" * 64) for name in names))


    def test_cross_architecture_export_runs_the_engine_tool_and_ships_the_target_tool(self):
        args = self.export_args()
        args.bootstrap = True
        args.registry_repository = "mirror.example.com/copies/registry"
        def write_archive(*_):
            (args.state / "export").mkdir()
            (args.state / "export/mirror_000001.tar").write_bytes(b"archive")
        def save(tool, target, arch, registry_repository):
            target.write_bytes(b"bootstrap")
            return "ontomato-offline-registry:3.1.2-" + arch
        with patch.object(mirror, "engine_arch", return_value="amd64"), \
                patch.object(mirror, "ensure_tool", side_effect=lambda state, arch: mirror.tool_image(arch)) as tool, \
                patch.object(mirror, "save_bootstrap", side_effect=save) as bootstrap, \
                patch.object(mirror, "mirror", side_effect=write_archive) as invoke:
            mirror.export(args)
        self.assertEqual([call.args[1] for call in tool.call_args_list], ["amd64", "arm64"])
        self.assertEqual(invoke.call_args.args[0], mirror.tool_image("amd64"))
        self.assertEqual(bootstrap.call_args.args[0], mirror.tool_image("arm64"))
        self.assertEqual(bootstrap.call_args.args[3], "mirror.example.com/copies/registry")
        self.assertEqual(mirror.verify_bundle(args.output)["tool"], mirror.tool_image("arm64"))

    def test_bootstrap_registry_keeps_the_pinned_digest_from_any_repository(self):
        with patch.object(mirror, "resolve_image", side_effect=ValueError("stop")) as resolve, \
                self.assertRaisesRegex(ValueError, "stop"):
            mirror.save_bootstrap("tool", self.root / "bootstrap.tar.gz", "arm64", "mirror.example.com/copies/registry")
        self.assertEqual(resolve.call_args.args, ("mirror.example.com/copies/registry@" + mirror.REGISTRY_DIGEST, "arm64"))
        self.assertEqual(mirror.REGISTRY_REPOSITORY, "docker.io/library/registry")

    def test_tool_build_runs_no_target_architecture_instruction(self):
        _, unpack, final = (Path(mirror.HERE) / "Dockerfile").read_text().split("\nFROM ")
        self.assertTrue(unpack.startswith("--platform=$BUILDPLATFORM "))
        self.assertNotIn("RUN ", final)


if __name__ == "__main__":
    unittest.main()
