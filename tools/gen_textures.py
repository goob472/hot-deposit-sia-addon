import os, struct, sys, zlib

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

def png(path, px):
    h = len(px); w = len(px[0])
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in px)
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)

def hexc(s):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)

# 16x16 textures at vanilla resolution. The model maps uv [1,1,15,15], so each face is a 14x14 grid
# placed at pixel offset 1; the 1px ring around it is plain wood (only visible in break particles).
SIZE, OFF, N = 16, 1, 14

# Vanilla-like palettes: spruce-plank body, oak-toned facial disc, the three gold-block golds.
P = {
    'w': hexc('735432'), 'W': hexc('7E5D37'), 'v': hexc('684B2C'),   # wood / lighter wood / dark grain
    'x': hexc('4C3520'),                                              # carved groove
    'l': hexc('B08A55'), 'L': hexc('BE9862'), 'k': hexc('9B7747'),   # facial disc
    'g': hexc('E9B115'), 'G': hexc('FDF55F'), 'd': hexc('B26411'),   # gold, gold sheen, deep gold
}

# Front: the owl. '.' marks where the 3x3 eye elements sit (cols 3-5 and 8-10, rows 4-6); filled with disc.
FRONT = [
    "gggggggggggggg",
    "gwvwwWwwwWwwvg",
    "gwGwwwwwwwwGwg",
    "gllGgllllgGllg",
    "gll...ll...llg",
    "gLl...kk...lLg",
    "gll...Gg...llg",
    "gxlllldgllllxg",
    "gwxxkllllkxxwg",
    "gxwwxxwwxxwwxg",
    "gwxxwWxxWwxxwg",
    "gwWwwwwwwwwWwg",
    "gxwwxxwwxxwwxg",
    "gggggggggggggg",
]

def side():
    rows = [list(r) for r in [
        "gggggggggggggg",
        "gwwwwwWwwwwwwg",
        "gwvwwwwwwwwvwg",
        "gwwwwwwwwwwwwg",
        "gwwwwwwwwwwwwg",
        "gWwwwwwwwwwwWg",
        "gwwwwwwwwwwwwg",
        "gwwwwwwwwwwwwg",
        "gwwwwwwwwwwwwg",
        "gwwwwwwwwwwwwg",
        "gwwwwwwwwwwwwg",
        "gvvvvvvvvvvvvg",
        "gwWwwwvwwwWwwg",
        "gggggggggggggg"]]
    for top in (2, 4, 6):  # folded wing: three nested carved feather chevrons with lighter edges
        for i in range(5):
            y = top + i
            if y > 10:
                break
            rows[y][2 + i] = 'x'
            rows[y][11 - i] = 'x'
            if y + 1 <= 10 and i < 4 and rows[y + 1][2 + i] != 'x':
                rows[y + 1][2 + i] = 'W'
    return ["".join(r) for r in rows]

SIDE = side()

# Top: one 7x7 quadrant mirrored both ways, so the lid is symmetric left-right and front-back.
TOP_QUADRANT = [
    "ggggggg",
    "gGwwwww",
    "gwwxwww",
    "gvvvvvv",
    "gwwwwww",
    "gwWwwwg",
    "gwwwwgG",
]
TOP = [r + r[::-1] for r in TOP_QUADRANT]
TOP += TOP[::-1]

for name, rows in (('front', FRONT), ('side', SIDE), ('top', TOP)):
    assert len(rows) == N and all(len(r) == N for r in rows), name
    border = rows[0] + rows[-1] + ''.join(r[0] + r[-1] for r in rows)
    assert set(border) == {'g'}, name + ': outline must be solid gold'
assert all(r == r[::-1] for r in TOP) and TOP == TOP[::-1], 'top must be symmetric'
assert all(r == r[::-1] for r in FRONT[8:13]), 'feathers below the face must be symmetric'

def texture(rows):
    px = [[P['w'] for _ in range(SIZE)] for _ in range(SIZE)]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            px[y + OFF][x + OFF] = P['l' if c == '.' else c]
    return px

png(os.path.join(out, 'hot_deposit_front.png'), texture(FRONT))
png(os.path.join(out, 'hot_deposit_side.png'), texture(SIDE))
png(os.path.join(out, 'hot_deposit_top.png'), texture(TOP))

# Eyes: 3x3 pixel art in the top-left of a 16x16 texture; the eye elements map uv [0,0,3,3].
# Open: purple iris, dark pupil, light highlight. Closed: wooden lid with a purple lash line.
EYE_P = {'O': hexc('5A2A8C'), 'E': hexc('A855F7'), 'H': hexc('E9D5FF'), 'p': hexc('2A0B45'),
         'm': hexc('9B7747'), 's': hexc('5B2A8C')}

def eye(art, fill):
    px = [[EYE_P[fill] for _ in range(SIZE)] for _ in range(SIZE)]
    for y, row in enumerate(art):
        for x, c in enumerate(row):
            px[y][x] = EYE_P[c]
    return px

png(os.path.join(out, 'hot_deposit_eye_open.png'), eye(["OHO", "EpE", "OEO"], 'O'))
png(os.path.join(out, 'hot_deposit_eye_closed.png'), eye(["mmm", "sss", "msm"], 'm'))
print('textures written')
