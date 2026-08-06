import random
import pygame

pygame.init()
FONT = pygame.font.SysFont('Arial', 18)
BIG_FONT = pygame.font.SysFont('Arial', 22)

BG_COLOR = (200, 200, 200)
GRID_COLOR = (100, 100, 100)
CLOSED_COLOR = (150, 150, 150)
OPEN_COLOR = (220, 220, 220)
MINE_COLOR = (200, 50, 50)
MENU_BG = (40, 40, 40)
BTN_COLOR = (80, 80, 80)
BTN_BORDER = (255, 255, 255)

TEXT_COLORS = {
    1: (0, 0, 255), 2: (0, 128, 0), 3: (255, 0, 0),
    4: (0, 0, 128), 5: (128, 0, 0), 6: (0, 128, 128),
    7: (0, 0, 0), 8: (128, 128, 128)
}


class GuessFreeMinesweeper:
    def __init__(self, width, height, mines_count):
        self.width = width
        self.height = height
        self.mines_count = mines_count
        self.states = [['closed' for _ in range(width)] for _ in range(height)]
        self.board = None
        self.first_click = True
        self.game_over = False
        self.won = False

    def generate_board(self, start_x, start_y):
        attempts = 0
        while attempts < 2000:
            board = [[0 for _ in range(self.width)] for _ in range(self.height)]
            mines_placed = 0
            while mines_placed < self.mines_count:
                rx = random.randint(0, self.width - 1)
                ry = random.randint(0, self.height - 1)
                if abs(rx - start_x) <= 1 and abs(ry - start_y) <= 1: continue
                if board[ry][rx] == -1: continue
                board[ry][rx] = -1
                mines_placed += 1

            for y in range(self.height):
                for x in range(self.width):
                    if board[y][x] == -1: continue
                    count = 0
                    for dy in [-1, 0, 1]:
                        for dx in [-1, 0, 1]:
                            nx, ny = x + dx, y + dy
                            if 0 <= nx < self.width and 0 <= ny < self.height:
                                if board[ny][nx] == -1: count += 1
                    board[y][x] = count

            if self.is_solvable(board, start_x, start_y):
                self.board = board
                return
            attempts += 1
        self.board = board

    def is_solvable(self, board, start_x, start_y):
        virtual_states = [['closed' for _ in range(self.width)] for _ in range(self.height)]

        def check_and_reveal_zeroes(sx, sy):
            queue = [(sx, sy)]
            virtual_states[sy][sx] = 'opened'
            while queue:
                cx, cy = queue.pop(0)
                if board[cy][cx] == 0:
                    for dy in [-1, 0, 1]:
                        for dx in [-1, 0, 1]:
                            nx, ny = cx + dx, cy + dy
                            if 0 <= nx < self.width and 0 <= ny < self.height:
                                if virtual_states[ny][nx] == 'closed':
                                    virtual_states[ny][nx] = 'opened'
                                    queue.append((nx, ny))

        check_and_reveal_zeroes(start_x, start_y)
        updated = True
        while updated:
            updated = False
            for y in range(self.height):
                for x in range(self.width):
                    if virtual_states[y][x] != 'opened' or board[y][x] <= 0: continue
                    closed_neighbors = []
                    flagged_count = 0
                    for dy in [-1, 0, 1]:
                        for dx in [-1, 0, 1]:
                            nx, ny = x + dx, y + dy
                            if 0 <= nx < self.width and 0 <= ny < self.height:
                                if virtual_states[ny][nx] == 'closed':
                                    closed_neighbors.append((nx, ny))
                                elif virtual_states[ny][nx] == 'flagged':
                                    flagged_count += 1

                    if board[y][x] == len(closed_neighbors) + flagged_count and len(closed_neighbors) > 0:
                        for nx, ny in closed_neighbors: virtual_states[ny][nx] = 'flagged'
                        updated = True
                    if board[y][x] == flagged_count and len(closed_neighbors) > 0:
                        for nx, ny in closed_neighbors: check_and_reveal_zeroes(nx, ny)
                        updated = True

        for y in range(self.height):
            for x in range(self.width):
                if board[y][x] != -1 and virtual_states[y][x] != 'opened': return False
        return True

    def click(self, x, y, button):
        if self.game_over or self.won: return
        if self.first_click and button == 1:
            self.generate_board(x, y)
            self.first_click = False

        if button == 1:
            if self.states[y][x] == 'closed':
                if self.board[y][x] == -1:
                    self.game_over = True
                elif self.board[y][x] == 0:
                    self.reveal_zeroes(x, y)
                else:
                    self.states[y][x] = 'opened'
            elif self.states[y][x] == 'opened' and self.board[y][x] > 0:
                self.chord_click(x, y)
        elif button == 3:
            if self.states[y][x] == 'closed':
                self.states[y][x] = 'flagged'
            elif self.states[y][x] == 'flagged':
                self.states[y][x] = 'closed'
        self.check_win()

    def chord_click(self, x, y):
        target_mines = self.board[y][x]
        flagged_count = 0
        neighbors = []
        for dy in [-1, 0, 1]:
            for dx in [-1, 0, 1]:
                nx, ny = x + dx, y + dy
                if 0 <= nx < self.width and 0 <= ny < self.height:
                    if (nx, ny) == (x, y): continue
                    neighbors.append((nx, ny))
                    if self.states[ny][nx] == 'flagged': flagged_count += 1
        if flagged_count == target_mines:
            for nx, ny in neighbors:
                if self.states[ny][nx] == 'closed':
                    if self.board[ny][nx] == -1:
                        self.game_over = True
                    elif self.board[ny][nx] == 0:
                        self.reveal_zeroes(nx, ny)
                    else:
                        self.states[ny][nx] = 'opened'

    def reveal_zeroes(self, start_x, start_y):
        queue = [(start_x, start_y)]
        self.states[start_y][start_x] = 'opened'
        while queue:
            cx, cy = queue.pop(0)
            if self.board[cy][cx] == 0:
                for dy in [-1, 0, 1]:
                    for dx in [-1, 0, 1]:
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < self.width and 0 <= ny < self.height:
                            if self.states[ny][nx] == 'closed':
                                self.states[ny][nx] = 'opened'
                                queue.append((nx, ny))

    def check_win(self):
        if self.first_click: return
        for y in range(self.height):
            for x in range(self.width):
                if self.board[y][x] != -1 and self.states[y][x] != 'opened': return
        self.won = True


def show_menu():
    menu_screen = pygame.display.set_mode((500, 500))
    pygame.display.set_caption("Выбор сложности")
    c_w, c_h, c_m = 12, 12, 20

    while True:
        menu_screen.fill(MENU_BG)
        btn_easy = pygame.Rect(50, 30, 400, 45)
        btn_medium = pygame.Rect(50, 90, 400, 45)
        btn_hard = pygame.Rect(50, 150, 400, 45)

        custom_panel_y = 250
        btn_w_minus = pygame.Rect(230, custom_panel_y, 35, 30)
        btn_w_plus = pygame.Rect(330, custom_panel_y, 35, 30)
        btn_h_minus = pygame.Rect(230, custom_panel_y + 40, 35, 30)
        btn_h_plus = pygame.Rect(330, custom_panel_y + 40, 35, 30)
        btn_m_minus = pygame.Rect(230, custom_panel_y + 80, 35, 30)
        btn_m_plus = pygame.Rect(330, custom_panel_y + 80, 35, 30)
        btn_custom_start = pygame.Rect(50, custom_panel_y + 140, 400, 45)

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                pygame.quit()
                return None
            if event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
                pos = pygame.mouse.get_pos()
                if btn_easy.collidepoint(pos): return (9, 9, 10)
                if btn_medium.collidepoint(pos): return (16, 16, 40)
                if btn_hard.collidepoint(pos): return (30, 16, 99)
                if btn_custom_start.collidepoint(pos):
                    c_m = min(c_m, (c_w * c_h) - 9)
                    return (c_w, c_h, max(1, c_m))
                if btn_w_minus.collidepoint(pos): c_w = max(5, c_w - 1)
                if btn_w_plus.collidepoint(pos): c_w = min(40, c_w + 1)
                if btn_h_minus.collidepoint(pos): c_h = max(5, c_h - 1)
                if btn_h_plus.collidepoint(pos): c_h = min(25, c_h + 1)
                if btn_m_minus.collidepoint(pos): c_m = max(1, c_m - 1)
                if btn_m_plus.collidepoint(pos): c_m = min((c_w * c_h) - 9, c_m + 1)

        pygame.draw.rect(menu_screen, BTN_COLOR, btn_easy)
        pygame.draw.rect(menu_screen, BTN_BORDER, btn_easy, 2)
        menu_screen.blit(FONT.render("1. Легкий (9x9, 10 мин)", True, (255, 255, 255)),
                         (btn_easy.x + 20, btn_easy.y + 12))

        pygame.draw.rect(menu_screen, BTN_COLOR, btn_medium)
        pygame.draw.rect(menu_screen, BTN_BORDER, btn_medium, 2)
        menu_screen.blit(FONT.render("2. Средний (16x16, 40 мин)", True, (255, 255, 255)),
                         (btn_medium.x + 20, btn_medium.y + 12))

        pygame.draw.rect(menu_screen, BTN_COLOR, btn_hard)
        pygame.draw.rect(menu_screen, BTN_BORDER, btn_hard, 2)
        menu_screen.blit(FONT.render("3. Сложный (30x16, 99 мин)", True, (255, 255, 255)),
                         (btn_hard.x + 20, btn_hard.y + 12))

        menu_screen.blit(FONT.render("4. Настраиваемый режим:", True, (255, 200, 0)), (50, custom_panel_y - 30))

        menu_screen.blit(FONT.render("Ширина X:", True, (255, 255, 255)), (50, custom_panel_y + 5))
        menu_screen.blit(FONT.render(str(c_w), True, (255, 255, 255)), (285, custom_panel_y + 5))
        pygame.draw.rect(menu_screen, (150, 50, 50), btn_w_minus)
        pygame.draw.rect(menu_screen, (50, 150, 50), btn_w_plus)
        menu_screen.blit(FONT.render("-", True, (255, 255, 255)), (btn_w_minus.x + 14, btn_w_minus.y + 4))
        menu_screen.blit(FONT.render("+", True, (255, 255, 255)), (btn_w_plus.x + 12, btn_w_plus.y + 4))

        menu_screen.blit(FONT.render("Высота Y:", True, (255, 255, 255)), (50, custom_panel_y + 45))
        menu_screen.blit(FONT.render(str(c_h), True, (255, 255, 255)), (285, custom_panel_y + 45))
        pygame.draw.rect(menu_screen, (150, 50, 50), btn_h_minus)
        pygame.draw.rect(menu_screen, (50, 150, 50), btn_h_plus)
        menu_screen.blit(FONT.render("-", True, (255, 255, 255)), (btn_h_minus.x + 14, btn_h_minus.y + 4))
        menu_screen.blit(FONT.render("+", True, (255, 255, 255)), (btn_h_plus.x + 12, btn_h_plus.y + 4))

        menu_screen.blit(FONT.render("Мины:", True, (255, 255, 255)), (50, custom_panel_y + 85))
        menu_screen.blit(FONT.render(str(c_m), True, (255, 255, 255)), (285, custom_panel_y + 85))
        pygame.draw.rect(menu_screen, (150, 50, 50), btn_m_minus)
        pygame.draw.rect(menu_screen, (50, 150, 50), btn_m_plus)
        menu_screen.blit(FONT.render("-", True, (255, 255, 255)), (btn_m_minus.x + 14, btn_m_minus.y + 4))
        menu_screen.blit(FONT.render("+", True, (255, 255, 255)), (btn_m_plus.x + 12, btn_m_plus.y + 4))

        pygame.draw.rect(menu_screen, (100, 80, 150), btn_custom_start)
        pygame.draw.rect(menu_screen, BTN_BORDER, btn_custom_start, 2)
        menu_screen.blit(FONT.render("ИГРАТЬ СВОЙ УРОВЕНЬ", True, (255, 255, 255)),
                         (btn_custom_start.x + 95, btn_custom_start.y + 12))

        pygame.display.flip()


def play_game(config):
    w, h, m = config
    cell_size = 30
    window_width = max(w * cell_size, 450)
    screen = pygame.display.set_mode((window_width, h * cell_size + 60))
    pygame.display.set_caption("Логический Сапер")
    game = GuessFreeMinesweeper(w, h, m)
    btn_back = pygame.Rect(window_width - 140, h * cell_size + 12, 120, 35)

    running = True
    while running:
        screen.fill(BG_COLOR)
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                return False
            elif event.type == pygame.MOUSEBUTTONDOWN:
                mx, my = pygame.mouse.get_pos()
                if (game.game_over or game.won) and btn_back.collidepoint((mx, my)): return True
                gx, gy = mx // cell_size, my // cell_size
                if 0 <= gx < w and 0 <= gy < h: game.click(gx, gy, event.button)

        for y in range(h):
            for x in range(w):
                rect = pygame.Rect(x * cell_size, y * cell_size, cell_size, cell_size)
                state = game.states[y][x]
                if state == 'closed':
                    pygame.draw.rect(screen, CLOSED_COLOR, rect)
                elif state == 'flagged':
                    pygame.draw.rect(screen, (255, 200, 0), rect)
                elif state == 'opened':
                    pygame.draw.rect(screen, OPEN_COLOR, rect)
                    if game.board and game.board[y][x] > 0:
                        val = game.board[y][x]
                        txt = FONT.render(str(val), True, TEXT_COLORS.get(val, (0, 0, 0)))
                        screen.blit(txt, (x * cell_size + 10, y * cell_size + 5))
                if game.game_over and game.board and game.board[y][x] == -1: pygame.draw.rect(screen, MINE_COLOR, rect)
                pygame.draw.rect(screen, GRID_COLOR, rect, 1)

        status_text, text_color = "Игра идет...", (0, 0, 0)
        if game.game_over: status_text, text_color = "ВЗРЫВ! Конец игры.", (150, 0, 0)
        if game.won: status_text, text_color = "ПОБЕДА! Без угадывания!", (0, 120, 0)

        screen.blit(BIG_FONT.render(status_text, True, text_color), (15, h * cell_size + 15))
        if game.game_over or game.won:
            pygame.draw.rect(screen, (70, 70, 70), btn_back)
            pygame.draw.rect(screen, (255, 255, 255), btn_back, 1)
            screen.blit(FONT.render("В меню", True, (255, 255, 255)), (btn_back.x + 20, btn_back.y + 7))
        pygame.display.flip()


def main():
    looping = True
    while looping:
        config = show_menu()
        if config is None: break
        looping = play_game(config)
    pygame.quit()


if __name__ == '__main__':
    main()