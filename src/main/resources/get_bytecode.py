import dis
import sys
from pathlib import Path


def get_bytecode(filename):
    file = Path(filename)
    source = file.read_text()
    code_obj = compile(source, file, mode='exec')
    dis.dis(code_obj)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(1)

    get_bytecode(sys.argv[1])
