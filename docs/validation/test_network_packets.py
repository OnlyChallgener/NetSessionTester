"""Protocol-fixture and source-contract checks for the iOS diagnostics.

These tests intentionally do not execute Kotlin/Native. They validate independent
DNS/STUN/ICMP wire fixtures and assert that the Kotlin sources retain the protocol,
cancellation, and ownership contracts exercised by those fixtures.
"""

from __future__ import annotations

import ipaddress
from pathlib import Path
import struct
import unittest


ROOT = Path(__file__).resolve().parents[2]
PACKETS_SOURCE = ROOT / "shared/src/commonMain/kotlin/com/demonv/netsessiontester/core/NetworkPackets.kt"
IOS_SOURCE = ROOT / "shared/src/iosMain/kotlin/com/demonv/netsessiontester/ios/tools/IosNetworkTools.kt"


def dns_response(record_type: int, address: str) -> bytes:
    question = b"\x03www\x07example\x03com\x00" + struct.pack("!HH", record_type, 1)
    raw_address = ipaddress.ip_address(address).packed
    answer = b"\xc0\x0c" + struct.pack("!HHIH", record_type, 1, 60, len(raw_address)) + raw_address
    return struct.pack("!HHHHHH", 0x4E53, 0x8180, 1, 1, 0, 0) + question + answer


def stun_address(attribute_type: int, address: str, port: int, transaction_id: bytes, xor: bool = False) -> bytes:
    raw_address = ipaddress.ip_address(address).packed
    if xor:
        mask = bytes.fromhex("2112a442") + transaction_id
        raw_address = bytes(value ^ mask[index] for index, value in enumerate(raw_address))
        port ^= 0x2112
    family = 1 if len(raw_address) == 4 else 2
    value = bytes((0, family)) + struct.pack("!H", port) + raw_address
    return struct.pack("!HH", attribute_type, len(value)) + value


class NetworkPacketFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.packets_source = PACKETS_SOURCE.read_text(encoding="utf-8")
        cls.ios_source = IOS_SOURCE.read_text(encoding="utf-8")

    def test_dns_compressed_a_and_aaaa_fixtures(self) -> None:
        ipv4 = dns_response(1, "203.0.113.7")
        ipv6 = dns_response(28, "2001:db8::7")
        self.assertEqual(ipv4[-4:], ipaddress.ip_address("203.0.113.7").packed)
        self.assertEqual(ipv6[-16:], ipaddress.ip_address("2001:db8::7").packed)
        self.assertIn(b"\xc0\x0c", ipv4)
        for contract in ("DNS_AAAA", "skipDnsName", "responseCode", "flags and 0x0200"):
            self.assertIn(contract, self.packets_source)

    def test_stun_xor_mapping_and_rfc5780_attributes(self) -> None:
        transaction_id = bytes(range(12))
        attributes = b"".join(
            (
                stun_address(0x0020, "198.51.100.9", 54321, transaction_id, xor=True),
                stun_address(0x802C, "192.0.2.2", 3479, transaction_id),
                stun_address(0x802B, "192.0.2.1", 3478, transaction_id),
            )
        )
        response = struct.pack("!HHI", 0x0101, len(attributes), 0x2112A442) + transaction_id + attributes
        self.assertEqual(len(response), 20 + len(attributes))
        self.assertEqual(response[20:22], b"\x00\x20")
        for contract in ("0x0020", "0x802c", "0x802b", "transactionId"):
            self.assertIn(contract, self.packets_source)
        self.assertIn("公网映射本身不能证明 NAT 类型", self.ios_source)
        self.assertIn("备用测试无响应，不能把超时当作映射变化", self.ios_source)

    def test_icmp_echo_error_and_packet_too_big_fixtures(self) -> None:
        sequence = 77
        inner_ipv4 = bytes((0x45,)) + bytes(19) + struct.pack("!BBHHH", 8, 0, 0, 0x4E53, sequence)
        inner_ipv6 = bytes((0x60,)) + bytes(39) + struct.pack("!BBHHH", 128, 0, 0, 0x4E53, sequence)
        time_exceeded = struct.pack("!BBHI", 11, 0, 0, 0) + inner_ipv4
        fragmentation_needed = struct.pack("!BBHI", 3, 4, 0, 1400) + inner_ipv4
        packet_too_big = struct.pack("!BBHI", 2, 0, 0, 1280) + inner_ipv6
        unreachable = struct.pack("!BBHI", 1, 0, 0, 0) + inner_ipv6
        self.assertEqual([p[0] for p in (time_exceeded, fragmentation_needed, packet_too_big, unreachable)], [11, 3, 2, 1])
        self.assertEqual(struct.unpack("!I", packet_too_big[4:8])[0], 1280)
        for contract in ("embeddedSequenceMatches", "packetTooBig", "nextHopMtu", "destinationUnreachable"):
            self.assertIn(contract, self.packets_source)

    def test_native_cancellation_ownership_and_pmtu_contracts(self) -> None:
        for contract in (
            "O_NONBLOCK",
            "coerceAtMost(50)",
            "ensureActive()",
            "freeaddrinfo(head)",
            "freeifaddrs(head.value)",
            "getsockname",
            "finally {\n            close(fd)",
            "不把超时当作边界",
            "while (upper - lower > 1)",
        ):
            self.assertIn(contract, self.ios_source)
        self.assertNotIn(".format(", self.ios_source)


if __name__ == "__main__":
    unittest.main()
