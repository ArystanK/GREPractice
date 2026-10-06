package kz.arctan.grepractice.data

import kz.arctan.grepractice.model.Question

/**
 * Starter bank of original GRE Mathematics Subject Test–style questions, loaded on first launch.
 * Math is LaTeX between `$…$`; strings use `$$"""` so `$` and `\` need no escaping.
 */
object SampleQuestions {
    /** Bump when sample content changes so existing installs get the new versions (see [Repository]). */
    const val VERSION = 3

    private var counter = 0

    /** [choices] is a single string with choices separated by `;;`. */
    private fun q(topic: String, text: String, choices: String, correct: Char, explanation: String) =
        Question(
            id = "sample-" + (++counter).toString().padStart(2, '0'),
            topic = topic,
            text = text,
            choices = choices.split(";;").map { it.trim() },
            correctIndex = correct - 'A',
            explanation = explanation,
        )

    val all: List<Question> = listOf(
        // ---- Calculus ----
        q(
            "Calculus", $$"""$\displaystyle\lim_{x \to 0} \frac{1 - \cos x}{x^2} =$""",
            $$"""$0$ ;; $\frac{1}{4}$ ;; $\frac{1}{2}$ ;; $1$ ;; $\infty$""", 'C',
            $$"""Use $1 - \cos x = \frac{x^2}{2} - \frac{x^4}{24} + \cdots$, so the ratio tends to $\frac{1}{2}$.""",
        ),
        q(
            "Calculus", $$"""$\displaystyle\int_0^{\pi} x \sin x \, dx =$""",
            $$"""$0$ ;; $1$ ;; $\frac{\pi}{2}$ ;; $\pi$ ;; $2\pi$""", 'D',
            $$"""By parts: $\left[-x\cos x\right]_0^{\pi} + \int_0^{\pi} \cos x \, dx = \pi + 0 = \pi$.""",
        ),
        q(
            "Calculus", $$"""If $f(x) = x^x$ for $x > 0$, then $f'(1) =$""",
            $$"""$1$ ;; $0$ ;; $\frac{1}{e}$ ;; $e$ ;; $2$""", 'A',
            $$"""$f(x) = e^{x \ln x}$, so $f'(x) = x^x(\ln x + 1)$ and $f'(1) = 1 \cdot (0 + 1) = 1$.""",
        ),
        q(
            "Calculus", $$"""The region under $y = \sqrt{x}$ for $0 \le x \le 4$ is revolved about the $x$-axis. The volume of the resulting solid is""",
            $$"""$4\pi$ ;; $\frac{16\pi}{3}$ ;; $\frac{32\pi}{3}$ ;; $16\pi$ ;; $8\pi$""", 'E',
            $$"""Disk method: $\pi \int_0^4 (\sqrt{x})^2 \, dx = \pi \int_0^4 x \, dx = 8\pi$.""",
        ),
        q(
            "Calculus", $$"""$\displaystyle\sum_{n=1}^{\infty} \frac{1}{n(n+1)} =$""",
            $$"""$\frac{1}{2}$ ;; $1$ ;; $\frac{3}{2}$ ;; $2$ ;; The series diverges""", 'B',
            $$"""Telescoping: $\frac{1}{n(n+1)} = \frac{1}{n} - \frac{1}{n+1}$, so the partial sums are $1 - \frac{1}{N+1} \to 1$.""",
        ),
        q(
            "Calculus", $$"""The radius of convergence of the power series $\displaystyle\sum_{n=1}^{\infty} \frac{n!}{n^n} x^n$ is""",
            $$"""$0$ ;; $\frac{1}{e}$ ;; $1$ ;; $e$ ;; $\infty$""", 'D',
            $$"""The ratio of consecutive coefficients is $\left(\frac{n}{n+1}\right)^n \to \frac{1}{e}$, so $R = e$.""",
        ),
        q(
            "Calculus", $$"""The maximum value of $f(x, y) = xy$ on the circle $x^2 + y^2 = 8$ is""",
            $$"""$2$ ;; $2\sqrt{2}$ ;; $8$ ;; $16$ ;; $4$""", 'E',
            $$"""By AM–GM, $xy \le \frac{x^2 + y^2}{2} = 4$, with equality at $x = y = 2$.""",
        ),
        q(
            "Calculus", $$"""$\displaystyle\frac{d}{dx} \int_0^{x^2} e^{t^2} \, dt =$""",
            $$"""$e^{x^4}$ ;; $2x e^{x^4}$ ;; $2x e^{x^2}$ ;; $x^2 e^{x^4}$ ;; $4x^3 e^{x^4}$""", 'B',
            $$"""Fundamental theorem of calculus and the chain rule: $e^{(x^2)^2} \cdot 2x = 2x e^{x^4}$.""",
        ),
        q(
            "Calculus", $$"""The area of the region bounded by $y = x^2$ and $y = 2x$ is""",
            $$"""$\frac{2}{3}$ ;; $1$ ;; $\frac{8}{3}$ ;; $\frac{4}{3}$ ;; $4$""", 'D',
            $$"""The curves meet at $x = 0$ and $x = 2$; $\int_0^2 (2x - x^2) \, dx = 4 - \frac{8}{3} = \frac{4}{3}$.""",
        ),
        q(
            "Calculus", $$"""If $D$ is the unit disk $x^2 + y^2 \le 1$, then $\displaystyle\iint_D (x^2 + y^2) \, dA =$""",
            $$"""$\frac{\pi}{2}$ ;; $\frac{\pi}{4}$ ;; $\pi$ ;; $2\pi$ ;; $\frac{4\pi}{3}$""", 'A',
            $$"""Polar coordinates: $\int_0^{2\pi} \int_0^1 r^2 \cdot r \, dr \, d\theta = 2\pi \cdot \frac{1}{4} = \frac{\pi}{2}$.""",
        ),

        // ---- Differential equations ----
        q(
            "Differential Equations", $$"""If $y' = 2xy$ and $y(0) = 3$, then $y(1) =$""",
            $$"""$3$ ;; $e$ ;; $3e^2$ ;; $3e$ ;; $e^3$""", 'D',
            $$"""The equation is separable: $y = 3e^{x^2}$, so $y(1) = 3e$.""",
        ),
        q(
            "Differential Equations", $$"""If $y'' + 4y = 0$, $y(0) = 1$ and $y'(0) = 2$, then $y\left(\frac{\pi}{8}\right) =$""",
            $$"""$\sqrt{2}$ ;; $-1$ ;; $0$ ;; $1$ ;; $2$""", 'A',
            $$"""$y = \cos 2x + \sin 2x$, so $y\left(\frac{\pi}{8}\right) = \cos\frac{\pi}{4} + \sin\frac{\pi}{4} = \sqrt{2}$.""",
        ),

        // ---- Linear algebra ----
        q(
            "Linear Algebra", $$"""The determinant of the matrix $$\begin{pmatrix} 2 & 1 & 0 \\ 1 & 2 & 1 \\ 0 & 1 & 2 \end{pmatrix}$$ is""",
            $$"""$0$ ;; $2$ ;; $6$ ;; $8$ ;; $4$""", 'E',
            $$"""Expand along the first row: $2(4 - 1) - 1(2 - 0) + 0 = 4$.""",
        ),
        q(
            "Linear Algebra", $$"""A real $3 \times 3$ matrix $A$ has eigenvalues $1$, $2$ and $3$. What is the trace of $A^2$?""",
            $$"""$6$ ;; $14$ ;; $12$ ;; $36$ ;; $196$""", 'B',
            $$"""$A^2$ has eigenvalues $1, 4, 9$, and the trace is their sum, $14$.""",
        ),
        q(
            "Linear Algebra", $$"""If $T\colon \mathbb{R}^5 \to \mathbb{R}^3$ is a linear transformation that maps onto $\mathbb{R}^3$, then the dimension of the kernel of $T$ is""",
            $$"""$0$ ;; $1$ ;; $2$ ;; $3$ ;; $5$""", 'C',
            $$"""Rank–nullity: $\dim \ker T = 5 - \operatorname{rank} T = 5 - 3 = 2$.""",
        ),
        q(
            "Linear Algebra", $$"""Let $A$ be an $n \times n$ real matrix with $A^2 = A$. Which of the following must be true?""",
            $$"""$A$ is the identity or the zero matrix ;; $\det A = 1$ ;; $A$ is invertible ;; Every eigenvalue of $A$ is $0$ or $1$ ;; $\operatorname{tr} A = 0$""",
            'D',
            $$"""If $Av = \lambda v$ then $\lambda v = A^2 v = \lambda^2 v$, so $\lambda^2 = \lambda$. Any projection other than $0$ and $I$ is a counterexample to the other choices.""",
        ),

        // ---- Abstract algebra ----
        q(
            "Abstract Algebra", $$"""How many elements of order $2$ are there in the symmetric group $S_4$?""",
            $$"""$9$ ;; $3$ ;; $6$ ;; $10$ ;; $12$""", 'A',
            $$"""Transpositions ($\binom{4}{2} = 6$) plus products of two disjoint transpositions ($3$) gives $9$.""",
        ),
        q(
            "Abstract Algebra", $$"""Up to isomorphism, how many abelian groups of order $72$ are there?""",
            $$"""$2$ ;; $3$ ;; $4$ ;; $6$ ;; $9$""", 'D',
            $$"""$72 = 2^3 \cdot 3^2$, so the count is $p(3) \cdot p(2) = 3 \cdot 2 = 6$, where $p$ is the partition function.""",
        ),
        q(
            "Abstract Algebra", $$"""Which of the following rings is a field?""",
            $$"""$\mathbb{Z}/12\mathbb{Z}$ ;; $\mathbb{Z}[x]/(x^2 + 1)$ ;; $\mathbb{R}[x]/(x^2 - 1)$ ;; $\mathbb{Q}[x]/(x^2 - 2)$ ;; $\mathbb{Z}/9\mathbb{Z}$""", 'D',
            $$"""$x^2 - 2$ is irreducible over $\mathbb{Q}$, so $(x^2 - 2)$ is a maximal ideal. $\mathbb{Z}[x]/(x^2 + 1) \cong \mathbb{Z}[i]$ is not a field, and the others have zero divisors.""",
        ),
        q(
            "Abstract Algebra", $$"""The order of the element $(1, 4)$ in the group $\mathbb{Z}_4 \times \mathbb{Z}_6$ is""",
            $$"""$2$ ;; $4$ ;; $6$ ;; $24$ ;; $12$""", 'E',
            $$"""$1$ has order $4$ in $\mathbb{Z}_4$ and $4$ has order $3$ in $\mathbb{Z}_6$, so the order is $\operatorname{lcm}(4, 3) = 12$.""",
        ),

        // ---- Number theory ----
        q(
            "Number Theory", $$"""What is the remainder when $3^{100}$ is divided by $7$?""",
            $$"""$1$ ;; $2$ ;; $4$ ;; $3$ ;; $5$""", 'C',
            $$"""$3^6 \equiv 1 \pmod 7$ and $100 = 6 \cdot 16 + 4$, so $3^{100} \equiv 3^4 = 81 \equiv 4$.""",
        ),
        q(
            "Number Theory", $$"""$\varphi(100)$, where $\varphi$ is Euler's totient function, equals""",
            $$"""$20$ ;; $40$ ;; $50$ ;; $60$ ;; $80$""", 'B',
            $$"""$\varphi(100) = 100 \left(1 - \frac{1}{2}\right)\left(1 - \frac{1}{5}\right) = 40$.""",
        ),
        q(
            "Number Theory", $$"""The last two digits of $7^{2026}$ are""",
            $$"""$01$ ;; $07$ ;; $43$ ;; $93$ ;; $49$""", 'E',
            $$"""$7^4 = 2401 \equiv 1 \pmod{100}$ and $2026 \equiv 2 \pmod 4$, so $7^{2026} \equiv 7^2 = 49$.""",
        ),

        // ---- Real analysis ----
        q(
            "Real Analysis", $$"""Which of the following sets is uncountable?""",
            $$"""$\mathbb{Q}$ ;; The Cantor set ;; The set of algebraic numbers ;; The set of finite subsets of $\mathbb{N}$ ;; $\mathbb{Z} \times \mathbb{Q}$""", 'B',
            $$"""The Cantor set is in bijection with $\{0, 2\}^{\mathbb{N}}$ (ternary expansions). The others are countable unions of countable sets.""",
        ),
        q(
            "Real Analysis", $$"""Let $f(x) = x^2 \sin\frac{1}{x}$ for $x \ne 0$ and $f(0) = 0$. Which of the following is true?""",
            $$"""$f$ is not continuous at $0$ ;; $f$ is continuous but not differentiable at $0$ ;; $f'$ exists everywhere and is continuous at $0$ ;; $f$ is differentiable everywhere, but $f'$ is not continuous at $0$ ;; $f$ is unbounded on $(-1, 1)$""",
            'D',
            $$"""$f'(0) = \lim_{h \to 0} h \sin\frac{1}{h} = 0$, but for $x \ne 0$, $f'(x) = 2x \sin\frac{1}{x} - \cos\frac{1}{x}$, which has no limit at $0$.""",
        ),
        q(
            "Real Analysis", $$"""Let $f_n(x) = x^n$ on $[0, 1]$. Which of the following is true?""",
            $$"""$f_n$ converges uniformly on $[0, 1]$ ;; $f_n$ converges pointwise to a continuous function ;; $f_n$ converges pointwise to a discontinuous function ;; $f_n(1)$ does not converge ;; $f_n$ converges uniformly on $[0, 1)$""",
            'C',
            $$"""The pointwise limit is $0$ on $[0, 1)$ and $1$ at $x = 1$. Since $\sup_{[0,1)} x^n = 1$, the convergence is not uniform even on $[0, 1)$.""",
        ),

        // ---- Complex analysis ----
        q(
            "Complex Analysis", $$"""$\displaystyle\oint_{|z| = 2} \frac{e^z}{z - 1} \, dz$, with the circle oriented counterclockwise, equals""",
            $$"""$0$ ;; $2\pi i$ ;; $\pi i e$ ;; $2\pi i e$ ;; $\frac{2\pi i}{e}$""", 'D',
            $$"""Cauchy's integral formula: $2\pi i \cdot e^1 = 2\pi i e$.""",
        ),
        q(
            "Complex Analysis", $$"""The residue of $f(z) = \dfrac{1}{z^2(z - 1)}$ at $z = 0$ is""",
            $$"""$-2$ ;; $0$ ;; $-1$ ;; $1$ ;; $2$""", 'C',
            $$"""$\frac{1}{z - 1} = -(1 + z + z^2 + \cdots)$, so $f(z) = -\frac{1}{z^2} - \frac{1}{z} - \cdots$, and the coefficient of $\frac{1}{z}$ is $-1$.""",
        ),
        q(
            "Complex Analysis", $$"""The principal value of $i^i$ is""",
            $$"""$-1$ ;; $e^{-\pi/2}$ ;; $i$ ;; $e^{\pi/2}$ ;; $e^{i\pi/2}$""", 'B',
            $$"""$i^i = e^{i \operatorname{Log} i} = e^{i \cdot i\pi/2} = e^{-\pi/2}$, a real number.""",
        ),

        // ---- Topology ----
        q(
            "Topology", $$"""Which of the following subsets of $\mathbb{R}$ (standard topology) is compact?""",
            $$"""$(0, 1)$ ;; $\mathbb{Z}$ ;; $\{1/n : n \ge 1\}$ ;; $\mathbb{Q} \cap [0, 1]$ ;; $\{0\} \cup \{1/n : n \ge 1\}$""", 'E',
            $$"""It is closed (it contains its only limit point $0$) and bounded, so Heine–Borel applies. $\{1/n\}$ alone misses the limit point $0$.""",
        ),
        q(
            "Topology", $$"""Which of the following subsets of $\mathbb{R}^2$ is connected?""",
            $$"""$\mathbb{R}^2$ with a single point removed ;; $\{(x, y) : xy \ne 0\}$ ;; $\mathbb{R}^2$ with a line removed ;; $\{(x, y) : x^2 + y^2 \ne 1\}$ ;; $\mathbb{Q} \times \mathbb{Q}$""",
            'A',
            $$"""The punctured plane is path-connected. Removing the axes, a line or the unit circle disconnects the plane, and $\mathbb{Q}^2$ is totally disconnected.""",
        ),

        // ---- Discrete math ----
        q(
            "Discrete Mathematics", $$"""In how many distinct ways can the letters of MISSISSIPPI be arranged?""",
            $$"""$3{,}465$ ;; $11{,}550$ ;; $34{,}650$ ;; $69{,}300$ ;; $39{,}916{,}800$""", 'C',
            $$"""$\dfrac{11!}{4! \, 4! \, 2!} = 34{,}650$ (four S's, four I's, two P's).""",
        ),
        q(
            "Discrete Mathematics", $$"""How many edges does the complete graph $K_{10}$ have?""",
            $$"""$10$ ;; $45$ ;; $20$ ;; $90$ ;; $100$""", 'B',
            $$"""$\binom{10}{2} = 45$.""",
        ),
        q(
            "Discrete Mathematics", $$"""How many integers from $1$ to $1000$, inclusive, are divisible by $3$ or by $5$?""",
            $$"""$400$ ;; $466$ ;; $533$ ;; $467$ ;; $600$""", 'D',
            $$"""Inclusion–exclusion: $333 + 200 - 66 = 467$.""",
        ),

        // ---- Probability & statistics ----
        q(
            "Probability & Statistics", $$"""Two fair dice are rolled. Given that at least one die shows a $3$, what is the probability that the sum is $7$?""",
            $$"""$\frac{2}{11}$ ;; $\frac{1}{6}$ ;; $\frac{1}{11}$ ;; $\frac{1}{3}$ ;; $\frac{2}{9}$""", 'A',
            $$"""$11$ outcomes contain a $3$; of these, $(3, 4)$ and $(4, 3)$ sum to $7$.""",
        ),
        q(
            "Probability & Statistics", $$"""If $X$ is uniformly distributed on $[0, 1]$, the variance of $X$ is""",
            $$"""$\frac{1}{6}$ ;; $\frac{1}{4}$ ;; $\frac{1}{3}$ ;; $\frac{1}{2}$ ;; $\frac{1}{12}$""", 'E',
            $$"""$E[X^2] - E[X]^2 = \frac{1}{3} - \frac{1}{4} = \frac{1}{12}$.""",
        ),

        // ---- Geometry ----
        q(
            "Geometry", $$"""The distance from the point $(1, 2, 3)$ to the plane $x + 2y + 2z = 2$ is""",
            $$"""$1$ ;; $2$ ;; $3$ ;; $9$ ;; $\frac{9}{\sqrt{5}}$""", 'C',
            $$"""$\dfrac{|1 + 4 + 6 - 2|}{\sqrt{1 + 4 + 4}} = \dfrac{9}{3} = 3$.""",
        ),
        q(
            "Geometry", $$"""The angle between the vectors $(1, 1, 0)$ and $(1, 0, 1)$ is""",
            $$"""$\frac{\pi}{6}$ ;; $\frac{\pi}{4}$ ;; $\frac{\pi}{2}$ ;; $\frac{\pi}{3}$ ;; $\frac{2\pi}{3}$""", 'D',
            $$"""$\cos\theta = \dfrac{1}{\sqrt{2} \cdot \sqrt{2}} = \dfrac{1}{2}$, so $\theta = \dfrac{\pi}{3}$.""",
        ),

        // ---- Numerical analysis ----
        q(
            "Numerical Analysis", $$"""Newton's method is applied to $f(x) = x^2 - 2$ with $x_0 = 1$. Then $x_2 =$""",
            $$"""$\frac{3}{2}$ ;; $\frac{4}{3}$ ;; $\frac{7}{5}$ ;; $\frac{577}{408}$ ;; $\frac{17}{12}$""", 'E',
            $$"""$x_1 = 1 - \frac{-1}{2} = \frac{3}{2}$, then $x_2 = \frac{3}{2} - \frac{1/4}{3} = \frac{17}{12}$.""",
        ),
    )
}
