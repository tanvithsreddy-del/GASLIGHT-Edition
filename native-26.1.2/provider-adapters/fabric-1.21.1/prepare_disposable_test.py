"""Create a new, isolated integration fixture. Never reuse a dirty world."""
import json,pathlib,re,sys
root=pathlib.Path(__file__).resolve().parent
suffix=sys.argv[1] if len(sys.argv)>1 else '01'
if not re.fullmatch(r'[0-9]{2}',suffix):raise SystemExit('Use a two-digit fixture suffix')
server=root/'run-server';name='phase-a-disposable-'+suffix;world=server/name
if world.exists():raise SystemExit('World already exists; unresolved journals must not be deleted or reused')
world.mkdir(parents=True);(world/'.gaslight-phase-a-disposable').write_text('GASLIGHT_PHASE_A_DISPOSABLE_V1\n')
settings=json.dumps(dict(layers=[dict(block='minecraft:bedrock',height=1),dict(block='minecraft:dirt',height=2),dict(block='minecraft:grass_block',height=1)],biome='minecraft:plains',features=False,lakes=False,structure_overrides=[]),separators=(',',':'))
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25588\nonline-mode=false\nenforce-secure-profile=false\nlevel-name='+name+'\nlevel-type=minecraft:flat\ngenerate-structures=false\nlevel-seed=44001\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-players=1\nsync-chunk-writes=true\ngenerator-settings='+settings+'\n')
if not (server/'eula.txt').exists():(server/'eula.txt').write_text('eula=false\n')
(server/'probe-server.txt').write_text('')
client=root/'run-client';client.mkdir(exist_ok=True)
(client/'probe-client.txt').write_text('connect-fixture-'+suffix)
if not (client/'options.txt').exists():(client/'options.txt').write_text('renderDistance:4\nsimulationDistance:4\nguiScale:2\nmipmapLevels:0\nmaxFps:60\nfullscreen:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nsoundCategory_master:0.0\n')
print('DISPOSABLE_FIXTURE_CREATED',world)
print('EULA acceptance must already be present or be supplied by the user before starting.')
